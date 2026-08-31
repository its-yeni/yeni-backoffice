package com.yeni.backoffice.core.commerce.service;
import com.yeni.backoffice.core.commerce.dto.LocationInventoryDtos.LocationInventoryResponse;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.PurchaseOrderStatus;
import com.yeni.backoffice.core.commerce.enums.StockTransferStatus;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class LocationInventoryService {
 private static final List<PurchaseOrderStatus> OPEN_PO = List.of(PurchaseOrderStatus.ORDERED, PurchaseOrderStatus.PARTIALLY_RECEIVED);
 private final StoreVariantInventoryRepository inventories;private final CommerceStoreRepository stores;private final ProductVariantRepository variants;private final ProductRepository products;private final StockTransferRepository transfers;private final StockTransferItemRepository transferItems;
 private final InventoryLotRepository lots;private final PurchaseOrderRepository purchaseOrders;private final PurchaseOrderItemRepository purchaseOrderItems;
 public LocationInventoryService(StoreVariantInventoryRepository i,CommerceStoreRepository s,ProductVariantRepository v,ProductRepository p,StockTransferRepository t,StockTransferItemRepository ti,
   InventoryLotRepository lots,PurchaseOrderRepository purchaseOrders,PurchaseOrderItemRepository purchaseOrderItems){inventories=i;stores=s;variants=v;products=p;transfers=t;transferItems=ti;this.lots=lots;this.purchaseOrders=purchaseOrders;this.purchaseOrderItems=purchaseOrderItems;}

 @Transactional(readOnly=true) public List<LocationInventoryResponse> list(Long storeId){
  List<StoreVariantInventory> rows=storeId==null?inventories.findAll():inventories.findByStoreId(storeId);if(rows.isEmpty())return List.of();
  Map<Long,CommerceStore> storeMap=stores.findAllById(rows.stream().map(StoreVariantInventory::getStoreId).distinct().toList()).stream().collect(Collectors.toMap(CommerceStore::getId,Function.identity()));
  Map<Long,ProductVariant> variantMap=variants.findAllById(rows.stream().map(StoreVariantInventory::getVariantId).distinct().toList()).stream().collect(Collectors.toMap(ProductVariant::getId,Function.identity()));
  Map<Long,Product> productMap=products.findAllById(variantMap.values().stream().map(ProductVariant::getProductId).distinct().toList()).stream().collect(Collectors.toMap(Product::getId,Function.identity()));
  Map<String,Integer> transit=new HashMap<>();transfers.findAllByOrderByIdDesc().stream().filter(t->t.getStatus()==StockTransferStatus.IN_TRANSIT).forEach(t->transferItems.findByTransferIdOrderById(t.getId()).forEach(item->transit.merge(t.getDestinationStoreId()+":"+item.getVariantId(),item.getQuantity(),Integer::sum)));
  Map<String,Integer> onOrder=outstandingPurchaseOrders();
  Map<String,Integer> lotAvailable=new HashMap<>();
  for(Object[] r:lots.sumAvailableByStoreVariant())lotAvailable.put(r[0]+":"+r[1],((Number)r[2]).intValue());
  return rows.stream().map(i->{
   CommerceStore s=storeMap.get(i.getStoreId());ProductVariant v=variantMap.get(i.getVariantId());Product p=v==null?null:productMap.get(v.getProductId());
   BigDecimal cost=i.getAverageUnitCost()==null?BigDecimal.ZERO:i.getAverageUnitCost();
   String key=i.getStoreId()+":"+i.getVariantId();
   return new LocationInventoryResponse(i.getId(),i.getStoreId(),s==null?"-":s.getStoreCode(),s==null?"-":s.getStoreName(),i.getVariantId(),v==null?null:v.getProductId(),p==null?"-":p.getProductName(),p==null?"-":p.getProductCode(),p==null?"-":p.getCategory(),v==null?"-":v.getSku(),v==null?"-":v.getOptionSummary(),
     i.getStockQuantity(),i.getReservedQuantity(),i.getAvailableQuantity(),
     transit.getOrDefault(key,0),onOrder.getOrDefault(key,0),lotAvailable.getOrDefault(key,0),
     i.getSafetyStock(),cost,cost.multiply(BigDecimal.valueOf(i.getStockQuantity())),i.isSaleEnabled(),v==null?"-":v.getSaleStatus().name(),i.getUpdatedAt());
  }).sorted(Comparator.comparing(LocationInventoryResponse::storeName).thenComparing(LocationInventoryResponse::productName).thenComparing(LocationInventoryResponse::sku)).toList();
 }

 /** 매장별 안전재고 인라인 편집. */
 @Transactional public void updateSafetyStock(Long inventoryId,int value){
  StoreVariantInventory inv=inventories.findById(inventoryId).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장 재고를 찾을 수 없습니다."));
  inv.updateSafetyStock(value);
 }

 /** ORDERED/PARTIALLY_RECEIVED 발주의 미입고 수량. key = "storeId:variantId". */
 @Transactional(readOnly=true) public Map<String,Integer> outstandingPurchaseOrders(){
  Map<Long,Long> poStore=purchaseOrders.findByStatusInOrderByIdDesc(OPEN_PO).stream().collect(Collectors.toMap(PurchaseOrder::getId,PurchaseOrder::getStoreId));
  Map<String,Integer> result=new HashMap<>();
  for(PurchaseOrderItem item:purchaseOrderItems.findOutstanding(OPEN_PO)){
   Long store=poStore.get(item.getPurchaseOrderId());if(store==null)continue;
   result.merge(store+":"+item.getVariantId(),item.outstandingQuantity(),Integer::sum);
  }
  return result;
 }
}
