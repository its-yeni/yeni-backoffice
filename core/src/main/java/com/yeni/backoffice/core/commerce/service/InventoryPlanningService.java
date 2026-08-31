package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.InventoryPlanningDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.*;
import com.yeni.backoffice.core.commerce.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class InventoryPlanningService {
    private static final int SALES_WINDOW_DAYS = 30;
    private static final int DEFAULT_LEAD_TIME_DAYS = 14;
    private static final int TARGET_COVER_DAYS = 30;
    private final InventoryLotRepository lots;
    private final StoreVariantInventoryRepository inventories;
    private final InventoryTransactionRepository transactions;
    private final CommerceStoreRepository stores;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final LocationInventoryService locationInventory;
    private final SupplierRepository suppliers;
    private final PurchaseOrderRepository purchaseOrders;
    private final PurchaseOrderItemRepository purchaseOrderItems;

    public InventoryPlanningService(InventoryLotRepository lots, StoreVariantInventoryRepository inventories,
            InventoryTransactionRepository transactions, CommerceStoreRepository stores,
            ProductVariantRepository variants, ProductRepository products, LocationInventoryService locationInventory,
            SupplierRepository suppliers, PurchaseOrderRepository purchaseOrders, PurchaseOrderItemRepository purchaseOrderItems) {
        this.lots=lots; this.inventories=inventories; this.transactions=transactions; this.stores=stores;
        this.variants=variants; this.products=products; this.locationInventory=locationInventory;
        this.suppliers=suppliers; this.purchaseOrders=purchaseOrders; this.purchaseOrderItems=purchaseOrderItems;
    }

    @Transactional(readOnly=true)
    public List<LotResponse> lots() {
        var rows=lots.findAllByOrderByExpirationDateAscIdAsc();
        Map<Long,CommerceStore> storeMap=stores.findAllById(rows.stream().map(InventoryLot::getStoreId).distinct().toList())
                .stream().collect(Collectors.toMap(CommerceStore::getId,Function.identity()));
        Map<Long,ProductVariant> variantMap=variants.findAllById(rows.stream().map(InventoryLot::getVariantId).distinct().toList())
                .stream().collect(Collectors.toMap(ProductVariant::getId,Function.identity()));
        Map<Long,Product> productMap=products.findAllById(variantMap.values().stream().map(ProductVariant::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getId,Function.identity()));
        LocalDate today=LocalDate.now();
        return rows.stream().map(l->{ProductVariant v=variantMap.get(l.getVariantId()); Product p=v==null?null:productMap.get(v.getProductId());
            Long days=l.getExpirationDate()==null?null:ChronoUnit.DAYS.between(today,l.getExpirationDate());
            String health=l.getAvailableQuantity()<=0?"DEPLETED":days==null?"NORMAL":days<0?"EXPIRED":days<=30?"CRITICAL":days<=90?"WARNING":"NORMAL";
            return new LotResponse(l.getId(),l.getStoreId(),name(storeMap.get(l.getStoreId())),l.getVariantId(),p==null?"-":p.getProductName(),v==null?"-":v.getSku(),v==null?"-":v.getOptionSummary(),l.getLotNo(),l.getManufacturedDate(),l.getExpirationDate(),l.getReceivedQuantity(),l.getAvailableQuantity(),days,health,l.getMemo());}).toList();
    }

    @Transactional(readOnly=true)
    public List<ReplenishmentResponse> replenishments(Integer requestedLeadTime) {
        LocalDateTime since=LocalDateTime.now().minusDays(SALES_WINDOW_DAYS);
        Map<Long,Integer> shipped=transactions.findByTypeAndCreatedAtGreaterThanEqual(InventoryTransactionType.SHIPMENT,since)
                .stream().collect(Collectors.groupingBy(InventoryTransaction::getVariantId,Collectors.summingInt(InventoryTransaction::getQuantity)));
        // 반품 재입고(RECEIPT · referenceType=RETURN)는 실제로 팔린 게 아니라 되돌아온 것이므로 판매속도에서 상쇄한다.
        Map<Long,Integer> returned=transactions.findByTypeAndCreatedAtGreaterThanEqual(InventoryTransactionType.RECEIPT,since)
                .stream().filter(t->"RETURN".equals(t.getReferenceType()))
                .collect(Collectors.groupingBy(InventoryTransaction::getVariantId,Collectors.summingInt(InventoryTransaction::getQuantity)));
        Map<Long,Integer> supplierLeadTime=leadTimeDaysByVariant();
        Map<Long,String> supplierName=supplierNameByVariant();
        return locationInventory.list(null).stream().map(row->{
            int leadTime=requestedLeadTime!=null?Math.max(1,Math.min(120,requestedLeadTime))
                    :supplierLeadTime.getOrDefault(row.variantId(),DEFAULT_LEAD_TIME_DAYS);
            int netSold=Math.max(0,shipped.getOrDefault(row.variantId(),0)-returned.getOrDefault(row.variantId(),0));
            BigDecimal daily=BigDecimal.valueOf(netSold).divide(BigDecimal.valueOf(SALES_WINDOW_DAYS),2,RoundingMode.HALF_UP);
            int leadDemand=daily.multiply(BigDecimal.valueOf(leadTime)).setScale(0,RoundingMode.CEILING).intValue();
            int rop=leadDemand+row.safetyStock(); int supply=row.availableQuantity()+row.inTransitQuantity()+row.onOrderQuantity();
            int target=daily.multiply(BigDecimal.valueOf(TARGET_COVER_DAYS+leadTime)).setScale(0,RoundingMode.CEILING).intValue()+row.safetyStock();
            int suggested=Math.max(0,target-supply); Integer days=daily.signum()==0?null:BigDecimal.valueOf(row.availableQuantity()).divide(daily,0,RoundingMode.FLOOR).intValue();
            String health=row.availableQuantity()<=0?"SOLD_OUT":supply<=rop?"REORDER":days!=null&&days>90?"EXCESS":"NORMAL";
            return new ReplenishmentResponse(row.storeId(),row.storeName(),row.variantId(),row.productName(),row.sku(),row.optionSummary(),
                    row.availableQuantity(),row.inTransitQuantity(),row.onOrderQuantity(),row.safetyStock(),daily,leadTime,
                    supplierName.getOrDefault(row.variantId(),"-"),rop,suggested,days,health);
        }).sorted(Comparator.comparingInt((ReplenishmentResponse r)->priority(r.health())).thenComparing(ReplenishmentResponse::productName)).toList();
    }

    /** variant → 그 SKU를 가장 최근에 발주한 공급처의 리드타임. */
    private Map<Long,Integer> leadTimeDaysByVariant() {
        Map<Long,Integer> supplierLead=suppliers.findAll().stream()
                .collect(Collectors.toMap(s->s.getId(),s->s.getLeadTimeDays()));
        Map<Long,Long> poSupplier=purchaseOrders.findAllByOrderByIdDesc().stream()
                .collect(Collectors.toMap(p->p.getId(),p->p.getSupplierId(),(a,b)->a));
        Map<Long,Integer> byVariant=new HashMap<>();
        for(var po:purchaseOrders.findAllByOrderByIdDesc())
            for(var item:purchaseOrderItems.findByPurchaseOrderIdOrderByIdAsc(po.getId()))
                byVariant.putIfAbsent(item.getVariantId(),supplierLead.getOrDefault(poSupplier.get(po.getId()),DEFAULT_LEAD_TIME_DAYS));
        return byVariant;
    }
    private Map<Long,String> supplierNameByVariant() {
        Map<Long,String> supplierName=suppliers.findAll().stream().collect(Collectors.toMap(s->s.getId(),s->s.getName()));
        Map<Long,String> byVariant=new HashMap<>();
        for(var po:purchaseOrders.findAllByOrderByIdDesc())
            for(var item:purchaseOrderItems.findByPurchaseOrderIdOrderByIdAsc(po.getId()))
                byVariant.putIfAbsent(item.getVariantId(),supplierName.getOrDefault(po.getSupplierId(),"-"));
        return byVariant;
    }

    @Transactional(readOnly=true)
    public InsightSummary summary() {
        var plans=replenishments(null); var lotRows=lots();
        long soldOut=plans.stream().filter(r->"SOLD_OUT".equals(r.health())).count();
        long reorder=plans.stream().filter(r->"REORDER".equals(r.health())).count();
        long excess=plans.stream().filter(r->"EXCESS".equals(r.health())).count();
        long expiring=lotRows.stream().filter(r->r.availableQuantity()>0&&r.daysToExpire()!=null&&r.daysToExpire()<=90).count();
        Set<String> risky=lotRows.stream().filter(r->r.availableQuantity()>0&&r.daysToExpire()!=null&&r.daysToExpire()<=90).map(r->r.storeId()+":"+r.variantId()).collect(Collectors.toSet());
        BigDecimal value=inventories.findAll().stream().filter(i->risky.contains(i.getStoreId()+":"+i.getVariantId()))
                .map(i->i.getAverageUnitCost().multiply(BigDecimal.valueOf(i.getStockQuantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
        return new InsightSummary(soldOut,reorder,expiring,excess,value);
    }
    private String name(CommerceStore s){return s==null?"-":s.getStoreName();}
    private int priority(String health){return switch(health){case "SOLD_OUT"->0;case "REORDER"->1;case "EXCESS"->2;default->3;};}
}
