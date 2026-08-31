package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.*;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryAddressRequest;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveRequest;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.service.PaymentApproveService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class CommerceOrderService {
    private final CommerceOrderRepository orderRepository;
    private final CommerceOrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionGroupRepository optionGroupRepository;
    private final ProductOptionValueRepository optionValueRepository;
    private final ProductAddonGroupRepository addonGroupRepository;
    private final ProductAddonItemRepository addonItemRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentApproveService paymentApproveService;
    private final CommerceStoreRepository storeRepository;
    private final InventoryTransactionService inventoryTransactionService;
    private final CommerceDeliveryService deliveryService;
    private final StoreVariantInventoryRepository storeInventoryRepository;
    private final InventoryLedgerService inventoryLedgerService;

    public CommerceOrderService(CommerceOrderRepository orderRepository, CommerceOrderItemRepository orderItemRepository,
            ProductRepository productRepository, ProductOptionGroupRepository optionGroupRepository,
            ProductOptionValueRepository optionValueRepository, ProductAddonGroupRepository addonGroupRepository,
            ProductAddonItemRepository addonItemRepository, ProductVariantRepository variantRepository, PaymentApproveService paymentApproveService,CommerceStoreRepository storeRepository,
            InventoryTransactionService inventoryTransactionService, CommerceDeliveryService deliveryService,
            StoreVariantInventoryRepository storeInventoryRepository, InventoryLedgerService inventoryLedgerService) {
        this.inventoryLedgerService=inventoryLedgerService;
        this.orderRepository=orderRepository; this.orderItemRepository=orderItemRepository; this.productRepository=productRepository;
        this.optionGroupRepository=optionGroupRepository; this.optionValueRepository=optionValueRepository;
        this.addonGroupRepository=addonGroupRepository; this.addonItemRepository=addonItemRepository;
        this.variantRepository=variantRepository;
        this.paymentApproveService=paymentApproveService;
        this.storeRepository=storeRepository;
        this.inventoryTransactionService=inventoryTransactionService;
        this.deliveryService=deliveryService;
        this.storeInventoryRepository=storeInventoryRepository;
    }

    @Transactional
    public CommerceOrderResponse createOrder(CommerceOrderCreateRequest request) {
        return createOrder(request, null);
    }

    @Transactional
    public CommerceOrderResponse createOrder(CommerceOrderCreateRequest request, Long fulfillmentStoreId) {
        validateCreateRequest(request);
        String orderNo=StringUtils.hasText(request.orderNo())?request.orderNo().trim():generateOrderNo();
        orderRepository.findByOrderNo(orderNo).ifPresent(o->{throw new ConflictException(ErrorCode.CONFLICT,"이미 사용 중인 주문번호입니다.");});
        Set<Long> productIds=new HashSet<>(), optionIds=new HashSet<>();
        request.items().forEach(i->{productIds.add(i.productId());safe(i.optionValueIds()).forEach(optionIds::add);safe(i.addOns()).forEach(a->productIds.add(a.productId()));});
        Map<Long,Product> products=productRepository.findAllByIdForUpdate(productIds).stream().collect(Collectors.toMap(Product::getId,Function.identity()));
        if(products.size()!=productIds.size())throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND);
        Map<Long,ProductOptionValue> values=optionIds.isEmpty()?Map.of():optionValueRepository.findAllByIdForUpdate(optionIds).stream().collect(Collectors.toMap(ProductOptionValue::getId,Function.identity()));
        if(values.size()!=optionIds.size())throw validation("존재하지 않는 옵션이 포함되어 있습니다.");
        List<ItemPlan> plans=new ArrayList<>();
        CommerceStore fulfillmentStore=fulfillmentStoreId == null ? null : storeRepository.findById(fulfillmentStoreId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "선택한 매장을 찾을 수 없습니다."));
        for(CommerceOrderItemCreateRequest item:request.items()){
            Product product=products.get(item.productId());
            OptionResult option=validateOptions(product,item,values);
            ProductVariant variant=findVariant(product.getId(),option.values());
            if(variant==null){product.decreaseStock(item.quantity());option.values().forEach(v->v.decreaseStock(item.quantity()));}
            else {
                if(fulfillmentStore==null)fulfillmentStore=storeRepository.findByStoreCode(product.getStoreCode()).orElse(null);
                // 매장이 지정되면 예약은 주문 저장 후 재고 원장을 통해 처리한다(주문번호를 감사 로그에 남기기 위함).
                // 매장이 없는 폴백 경로(순수 SKU 테스트 등)에서는 기존처럼 SKU 전역 예약만 건다.
                if(fulfillmentStore==null)variant.reserve(item.quantity());
            }
            BigDecimal configuredAmount=variant==null?option.amount():variant.getAdditionalPrice().add(extraOptionAmount(variant,option.values()));
            plans.add(new ItemPlan(product,configuredAmount,item.quantity(),option.summary(),option.ids(),false,variant));
            addAddons(product,item,products,plans);
        }
        BigDecimal productAmount=plans.stream().map(ItemPlan::amount).reduce(BigDecimal.ZERO,BigDecimal::add);
        String first=plans.get(0).product().getProductName();
        String representative=plans.size()==1?first:first+" 외 "+(plans.size()-1)+"건";
        CommerceOrder order=CommerceOrder.builder().orderNo(orderNo).buyerName(request.buyerName().trim()).buyerPhone(trimToNull(request.buyerPhone()))
                .productName(representative).productAmount(BigDecimal.ZERO).deliveryFee(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .payableAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO).cancelledAmount(BigDecimal.ZERO).stockRestored(false)
                .orderStatus(OrderStatus.PENDING_PAYMENT).paymentStatus(OrderPaymentStatus.READY).lastMessage("주문 생성 및 재고 예약이 완료되었습니다.").build();
        order.recalculateAmounts(productAmount,BigDecimal.ZERO,BigDecimal.ZERO);
        CommerceOrder saved=orderRepository.save(order);
        if(fulfillmentStore!=null)saved.assignStore(fulfillmentStore.getId(),fulfillmentStore.getStoreCode(),fulfillmentStore.getStoreName());
        List<CommerceOrderItem> savedItems=plans.stream().map(p->orderItemRepository.save(p.toEntity(saved.getId()))).toList();
        CommerceStore reservationStore=fulfillmentStore;
        plans.stream().filter(p->p.variant()!=null).forEach(p->{
            if(reservationStore!=null)
                inventoryLedgerService.reserve(reservationStore.getId(),p.variant().getId(),p.quantity(),
                        "주문 생성에 따른 재고 예약","ORDER",saved.getId(),"SYSTEM");
            else
                inventoryTransactionService.record(p.variant(),InventoryTransactionType.RESERVE,p.quantity(),
                        "주문 생성에 따른 재고 예약","ORDER",saved.getId(),"SYSTEM");
        });
        deliveryService.createForOrder(saved.getId(),saved.getBuyerName(),saved.getBuyerPhone(),request.delivery());
        return CommerceOrderResponse.from(saved,savedItems,deliveryService.findByOrderId(saved.getId()));
    }

    private OptionResult validateOptions(Product product,CommerceOrderItemCreateRequest item,Map<Long,ProductOptionValue> locked){
        List<ProductOptionGroup> groups=optionGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        Map<Long,ProductOptionGroup> groupMap=groups.stream().collect(Collectors.toMap(ProductOptionGroup::getId,Function.identity()));
        List<ProductOptionValue> selected=safe(item.optionValueIds()).stream().distinct().map(locked::get).toList();
        Map<Long,List<ProductOptionValue>> byGroup=selected.stream().collect(Collectors.groupingBy(ProductOptionValue::getOptionGroupId));
        if(byGroup.keySet().stream().anyMatch(id->!groupMap.containsKey(id)))throw validation("다른 상품의 옵션은 선택할 수 없습니다.");
        if(selected.stream().anyMatch(v->v.getSaleStatus()!=ProductSaleStatus.ON_SALE))throw validation("판매 중이 아닌 옵션이 포함되어 있습니다.");
        for(ProductOptionGroup g:groups){
            int count=byGroup.getOrDefault(g.getId(),List.of()).size(); int min=g.isRequiredOption()?Math.max(1,g.getMinSelection()):g.getMinSelection();
            if(count<min||count>g.getMaxSelection()||(g.getSelectionType()==OptionSelectionType.SINGLE&&count>1))throw validation(g.getGroupName()+" 옵션 선택 개수가 올바르지 않습니다.");
        }
        BigDecimal amount=selected.stream().map(ProductOptionValue::getAdditionalPrice).reduce(BigDecimal.ZERO,BigDecimal::add);
        String summary=groups.stream().filter(g->byGroup.containsKey(g.getId())).map(g->g.getGroupName()+": "+byGroup.get(g.getId()).stream().map(ProductOptionValue::getValueName).collect(Collectors.joining(", "))).collect(Collectors.joining(" / "));
        String ids=selected.stream().map(v->v.getId().toString()).collect(Collectors.joining(","));
        return new OptionResult(selected,amount,summary.isBlank()?null:summary,ids.isBlank()?null:ids);
    }

    private void addAddons(Product base,CommerceOrderItemCreateRequest item,Map<Long,Product> products,List<ItemPlan> plans){
        List<ProductAddonGroup> groups=addonGroupRepository.findByProductIdOrderBySortOrderAscIdAsc(base.getId());
        Map<Long,ProductAddonGroup> groupMap=groups.stream().collect(Collectors.toMap(ProductAddonGroup::getId,Function.identity()));
        Map<Long,Integer> totals=new HashMap<>();
        for(CommerceOrderAddonCreateRequest addon:safe(item.addOns())){
            ProductAddonGroup group=groupMap.get(addon.addonGroupId()); if(group==null)throw validation("다른 상품의 추가상품 그룹은 선택할 수 없습니다.");
            ProductAddonItem link=addonItemRepository.findByAddonGroupIdAndAddonProductId(group.getId(),addon.productId()).orElseThrow(()->validation("등록되지 않은 추가상품입니다."));
            if(addon.quantity()>link.getMaxQuantity())throw validation("추가상품 최대 선택 수량을 초과했습니다.");
            totals.merge(group.getId(),addon.quantity(),Integer::sum);
            int totalQuantity=Math.multiplyExact(addon.quantity(),item.quantity()); Product p=products.get(addon.productId()); p.decreaseStock(totalQuantity);
            plans.add(new ItemPlan(p,BigDecimal.ZERO,totalQuantity,group.getGroupName(),null,true,null));
        }
        for(ProductAddonGroup g:groups){int count=totals.getOrDefault(g.getId(),0);if(count<g.getMinQuantity()||count>g.getMaxQuantity())throw validation(g.getGroupName()+" 추가상품 선택 수량이 올바르지 않습니다.");}
    }

    @Transactional public CommerceOrderResponse createMockOrder(){
        List<CommerceOrderItemCreateRequest> items=productRepository.findAll().stream()
                .filter(p->p.getSaleStatus()==ProductSaleStatus.ON_SALE)
                .map(this::mockItem)
                .flatMap(Optional::stream)
                .limit(2)
                .toList();
        if(items.isEmpty())throw new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND,"판매 가능한 상품과 SKU를 먼저 등록해 주세요.");
        DeliveryAddressRequest delivery = new DeliveryAddressRequest(
                "포트폴리오 고객", "010-0000-0000", "06134",
                "서울특별시 강남구 테헤란로 123", "Yeni 빌딩 7층", "문 앞에 놓아주세요");
        return createOrder(new CommerceOrderCreateRequest(null,"포트폴리오 고객","010-0000-0000",items,delivery));
    }

    private Optional<CommerceOrderItemCreateRequest> mockItem(Product product){
        List<ProductVariant> variants=variantRepository.findByProductIdOrderBySortOrderAscIdAsc(product.getId());
        if(variants.isEmpty()){
            boolean available=!product.isInventoryManaged()||product.getStockQuantity()>0;
            return available?Optional.of(new CommerceOrderItemCreateRequest(product.getId(),1)):Optional.empty();
        }
        return variants.stream()
                .filter(v->v.getSaleStatus()==ProductSaleStatus.ON_SALE&&v.getAvailableQuantity()>0)
                .findFirst()
                .map(v->{
                    List<Long> optionIds="BASE".equals(v.getCombinationKey())||v.getCombinationKey().isBlank()
                            ?List.of()
                            :Arrays.stream(v.getCombinationKey().split(",")).map(String::trim).filter(s->!s.isEmpty()).map(Long::valueOf).toList();
                    return new CommerceOrderItemCreateRequest(product.getId(),1,optionIds,List.of());
                });
    }
    @Transactional public CommerceOrderResponse approvePayment(Long orderId){
        CommerceOrder order=orderRepository.findById(orderId).orElseThrow(()->new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        paymentApproveService.approvePayment(new PaymentApproveRequest(PgProvider.MOCK,order.getOrderNo(),order.getPayableAmount(),"KRW",order.getBuyerName(),order.getProductName(),"ORDER-PAY-"+order.getOrderNo(),"WEB",order.getStoreCode()==null?"PORTFOLIO":order.getStoreCode(),"CARD",order.getStoreId()));
        CommerceOrder refreshed=orderRepository.findById(orderId).orElseThrow(()->new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        return CommerceOrderResponse.from(refreshed,orderItemRepository.findByOrderIdOrderByIdAsc(orderId),deliveryService.findByOrderId(orderId));
    }
    @Transactional(readOnly=true) public List<CommerceOrderResponse> getOrders(){return getOrders(null);}
    @Transactional(readOnly=true) public List<CommerceOrderResponse> getOrders(Long storeId){
        List<CommerceOrder> orders=storeId==null?orderRepository.findAllByOrderByIdDesc():orderRepository.findByStoreIdOrderByIdDesc(storeId);
        return orders.stream().map(o->CommerceOrderResponse.from(o,orderItemRepository.findByOrderIdOrderByIdAsc(o.getId()),deliveryService.findByOrderId(o.getId()))).toList();
    }
    @Transactional(readOnly=true) public CommerceOrderResponse getOrder(Long orderId){CommerceOrder order=orderRepository.findById(orderId).orElseThrow(()->new NotFoundException(ErrorCode.ORDER_NOT_FOUND));return CommerceOrderResponse.from(order,orderItemRepository.findByOrderIdOrderByIdAsc(orderId),deliveryService.findByOrderId(orderId));}
    @Transactional(readOnly=true) public CommerceOrderSummaryResponse getSummary(){return getSummary(null);}
    @Transactional(readOnly=true) public CommerceOrderSummaryResponse getSummary(Long storeId){return CommerceOrderSummaryResponse.from(storeId==null?orderRepository.findAll():orderRepository.findByStoreIdOrderByIdDesc(storeId));}
    @Transactional public CommerceOrderResponse assignStore(Long orderId,Long storeId){if(storeId==null)return getOrder(orderId);CommerceOrder order=orderRepository.findById(orderId).orElseThrow(()->new NotFoundException(ErrorCode.ORDER_NOT_FOUND));CommerceStore store=storeRepository.findById(storeId).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장을 찾을 수 없습니다."));order.assignStore(store.getId(),store.getStoreCode(),store.getStoreName());return CommerceOrderResponse.from(order,orderItemRepository.findByOrderIdOrderByIdAsc(orderId),deliveryService.findByOrderId(orderId));}
    private void validateCreateRequest(CommerceOrderCreateRequest r){
        if(r==null||!StringUtils.hasText(r.buyerName()))throw validation("구매자명은 필수입니다.");
        if(r.items()==null||r.items().isEmpty())throw validation("주문 상품은 1개 이상이어야 합니다.");
        if(r.items().stream().anyMatch(i->i.productId()==null||i.quantity()<=0))throw validation("상품 ID와 1개 이상의 수량이 필요합니다.");
        if(r.items().stream().flatMap(i->safe(i.addOns()).stream()).anyMatch(a->a.addonGroupId()==null||a.productId()==null||a.quantity()<=0))throw validation("추가상품 정보가 올바르지 않습니다.");
    }
    private ValidationBusinessException validation(String m){return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,m);}
    // 옵션을 하나도 고르지 않은 주문은 옵션 조합 키로 매칭할 수 없다. 이런 상품에 "BASE"(상품 자체를 대표하는
    // 기본 SKU) 조합키를 가진 변형이 있으면 그걸 쓰고, 없으면 예전처럼 상품 단가/재고 경로(variant==null)로 처리한다.
    private ProductVariant findVariant(Long productId,List<ProductOptionValue> selected){
        List<ProductVariant> configured=variantRepository.findByProductIdOrderBySortOrderAscIdAsc(productId);
        if(configured.isEmpty())return null;
        if(selected.isEmpty()){
            ProductVariant base=configured.stream().filter(v->"BASE".equals(v.getCombinationKey())).findFirst().orElse(null);
            return base==null?null:variantRepository.findByIdForUpdate(base.getId()).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));
        }
        Set<String> ids=selected.stream().map(v->v.getId().toString()).collect(Collectors.toSet());
        ProductVariant found=configured.stream().filter(v->ids.containsAll(Arrays.asList(v.getCombinationKey().split(",")))).findFirst().orElse(null);
        if(found==null){
            // 조합 SKU를 따로 만들지 않은 상품인데 placeholder "기본" 옵션(추가금 0)만 고른 경우 → BASE SKU로 처리한다.
            boolean allDefault=selected.stream().allMatch(v->v.getAdditionalPrice()==null||v.getAdditionalPrice().signum()==0);
            ProductVariant base=configured.stream().filter(v->"BASE".equals(v.getCombinationKey())).findFirst().orElse(null);
            if(allDefault&&base!=null)found=base;
            else throw validation("선택한 옵션 조합은 판매 가능한 SKU가 아닙니다.");
        }
        return variantRepository.findByIdForUpdate(found.getId()).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));
    }
    private BigDecimal extraOptionAmount(ProductVariant variant,List<ProductOptionValue> selected){Set<String> variantIds=new HashSet<>(Arrays.asList(variant.getCombinationKey().split(",")));return selected.stream().filter(v->!variantIds.contains(v.getId().toString())).map(ProductOptionValue::getAdditionalPrice).reduce(BigDecimal.ZERO,BigDecimal::add);}
    private <T> List<T> safe(List<T> list){return list==null?List.of():list;}
    private String generateOrderNo(){return "ORD-"+LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase();}
    private String trimToNull(String v){return StringUtils.hasText(v)?v.trim():null;}
    private record OptionResult(List<ProductOptionValue> values,BigDecimal amount,String summary,String ids){}
    private record ItemPlan(Product product,BigDecimal optionAmount,int quantity,String summary,String ids,boolean addon,ProductVariant variant){
        BigDecimal amount(){return product.getSalePrice().add(optionAmount).multiply(BigDecimal.valueOf(quantity));}
        CommerceOrderItem toEntity(Long orderId){return CommerceOrderItem.createConfigured(orderId,product.getId(),variant==null?product.getProductCode():variant.getSku(),product.getProductName(),categoryName(),product.getSalePrice(),optionAmount,quantity,summary,ids,addon,variant==null?null:variant.getId());}
        String categoryName(){return org.springframework.util.StringUtils.hasText(product.getCategory())?product.getCategory().trim():"미분류";}
    }
}
