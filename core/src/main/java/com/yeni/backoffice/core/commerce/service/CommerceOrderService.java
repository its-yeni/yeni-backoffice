package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.*;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryAddressRequest;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.*;
import com.yeni.backoffice.core.commerce.scope.OperationalScope;
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
import com.yeni.backoffice.core.commerce.service.OrderCreationPlanningService.OrderPlan;
import com.yeni.backoffice.core.commerce.service.OrderCreationPlanningService.PlannedItem;

@Service
public class CommerceOrderService {
    private final CommerceOrderRepository orderRepository;
    private final CommerceOrderItemRepository orderItemRepository;
    private final ProductRepository productRepository;
    private final ProductVariantRepository variantRepository;
    private final PaymentApproveService paymentApproveService;
    private final CommerceStoreRepository storeRepository;
    private final CommerceDeliveryService deliveryService;
    private final CommerceOrderQueryService orderQueryService;
    private final OrderInventoryReservationService reservationService;
    private final OrderCreationPlanningService planningService;

    public CommerceOrderService(CommerceOrderRepository orderRepository, CommerceOrderItemRepository orderItemRepository,
            ProductRepository productRepository, ProductVariantRepository variantRepository, PaymentApproveService paymentApproveService,CommerceStoreRepository storeRepository,
            CommerceDeliveryService deliveryService, CommerceOrderQueryService orderQueryService,
            OrderInventoryReservationService reservationService, OrderCreationPlanningService planningService) {
        this.orderRepository=orderRepository; this.orderItemRepository=orderItemRepository; this.productRepository=productRepository;
        this.variantRepository=variantRepository;
        this.paymentApproveService=paymentApproveService;
        this.storeRepository=storeRepository;
        this.deliveryService=deliveryService;
        this.orderQueryService=orderQueryService;
        this.reservationService=reservationService;
        this.planningService=planningService;
    }

    @Transactional
    public CommerceOrderResponse createOrder(CommerceOrderCreateRequest request) {
        return createOrder(request, null);
    }

    @Transactional
    public CommerceOrderResponse createOrder(CommerceOrderCreateRequest request, Long fulfillmentStoreId) {
        OrderPlan orderPlan=planningService.plan(request);
        String orderNo=StringUtils.hasText(request.orderNo())?request.orderNo().trim():generateOrderNo();
        orderRepository.findByOrderNo(orderNo).ifPresent(o->{throw new ConflictException(ErrorCode.CONFLICT,"이미 사용 중인 주문번호입니다.");});
        CommerceStore fulfillmentStore=fulfillmentStoreId == null ? null : storeRepository.findById(fulfillmentStoreId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "선택한 매장을 찾을 수 없습니다."));
        for(PlannedItem item:orderPlan.items()){
            if(item.variant()!=null&&fulfillmentStore==null) {
                fulfillmentStore=storeRepository.findByStoreCode(item.product().getStoreCode()).orElse(null);
            }
            if(item.addon())reservationService.reserveAddon(item.product(),item.quantity());
            else reservationService.reserveSellable(item.product(),item.optionValues(),item.variant(),fulfillmentStore,item.quantity());
        }
        BigDecimal productAmount=orderPlan.productAmount();
        String first=orderPlan.items().get(0).product().getProductName();
        String representative=orderPlan.items().size()==1?first:first+" 외 "+(orderPlan.items().size()-1)+"건";
        CommerceOrder order=CommerceOrder.builder().orderNo(orderNo).buyerName(request.buyerName().trim()).buyerPhone(trimToNull(request.buyerPhone()))
                .productName(representative).productAmount(BigDecimal.ZERO).deliveryFee(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .payableAmount(BigDecimal.ZERO).paidAmount(BigDecimal.ZERO).cancelledAmount(BigDecimal.ZERO).stockRestored(false)
                .orderStatus(OrderStatus.PENDING_PAYMENT).paymentStatus(OrderPaymentStatus.READY).lastMessage("주문 생성 및 재고 예약이 완료되었습니다.").build();
        order.recalculateAmounts(productAmount,BigDecimal.ZERO,BigDecimal.ZERO);
        CommerceOrder saved=orderRepository.save(order);
        if(fulfillmentStore!=null)saved.assignStore(fulfillmentStore.getId(),fulfillmentStore.getStoreCode(),fulfillmentStore.getStoreName());
        List<CommerceOrderItem> savedItems=orderPlan.items().stream().map(p->orderItemRepository.save(toEntity(p,saved.getId()))).toList();
        reservationService.recordVariantReservations(saved.getId(),fulfillmentStore,orderPlan.items().stream()
                .filter(p->p.variant()!=null)
                .map(p->new OrderInventoryReservationService.VariantReservation(p.variant(),p.quantity()))
                .toList());
        deliveryService.createForOrder(saved.getId(),saved.getBuyerName(),saved.getBuyerPhone(),request.delivery());
        return CommerceOrderResponse.from(saved,savedItems,deliveryService.findByOrderId(saved.getId()));
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
    @Transactional(readOnly=true) public List<CommerceOrderResponse> getOrders(){return orderQueryService.getOrders();}
    @Transactional(readOnly=true) public List<CommerceOrderResponse> getOrders(Long storeId){
        return orderQueryService.getOrders(storeId);
    }
    @Transactional(readOnly=true) public List<CommerceOrderResponse> getOrdersInScope(OperationalScope scope){
        return orderQueryService.getOrdersInScope(scope);
    }
    @Transactional(readOnly=true) public CommerceOrderResponse getOrder(Long orderId){return orderQueryService.getOrder(orderId);}
    @Transactional(readOnly=true) public CommerceOrderSummaryResponse getSummary(){return getSummary(null);}
    @Transactional(readOnly=true) public CommerceOrderSummaryResponse getSummary(Long storeId){return orderQueryService.getSummary(storeId);}
    @Transactional(readOnly=true) public CommerceOrderSummaryResponse getSummaryInScope(OperationalScope scope){return orderQueryService.getSummaryInScope(scope);}
    @Transactional public CommerceOrderResponse assignStore(Long orderId,Long storeId){if(storeId==null)return getOrder(orderId);CommerceOrder order=orderRepository.findById(orderId).orElseThrow(()->new NotFoundException(ErrorCode.ORDER_NOT_FOUND));CommerceStore store=storeRepository.findById(storeId).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND,"매장을 찾을 수 없습니다."));order.assignStore(store.getId(),store.getStoreCode(),store.getStoreName());return CommerceOrderResponse.from(order,orderItemRepository.findByOrderIdOrderByIdAsc(orderId),deliveryService.findByOrderId(orderId));}
    private String generateOrderNo(){return "ORD-"+LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))+"-"+UUID.randomUUID().toString().substring(0,6).toUpperCase();}
    private String trimToNull(String v){return StringUtils.hasText(v)?v.trim():null;}
    private CommerceOrderItem toEntity(PlannedItem item,Long orderId){
        Product product=item.product(); ProductVariant variant=item.variant();
        String sku=variant==null?product.getProductCode():variant.getSku();
        String category=StringUtils.hasText(product.getCategory())?product.getCategory().trim():"미분류";
        return CommerceOrderItem.createConfigured(orderId,product.getId(),sku,product.getProductName(),category,
                product.getSalePrice(),item.optionAmount(),item.quantity(),item.optionSummary(),item.optionValueIds(),
                item.addon(),variant==null?null:variant.getId());
    }
}
