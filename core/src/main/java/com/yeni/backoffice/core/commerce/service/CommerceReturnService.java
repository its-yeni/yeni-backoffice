package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceReturnDtos.ReturnResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.entity.CommerceReturn;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.enums.ReturnResponsibility;
import com.yeni.backoffice.core.commerce.enums.ReturnStatus;
import com.yeni.backoffice.core.commerce.event.ReturnCompletedEvent;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceReturnRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 반품 접수 → 검수 → 완료(재고 복원 + 환불 확정)/반려 흐름을 담당한다.
 * 재고 복원은 검수 완료 시점에 이 서비스가 직접 처리하지만, "환불(PG 취소)"은 여기서 직접 호출하지 않는다 —
 * {@link ReturnCompletedEvent}를 발행하기만 하고, 실제 PG 취소/취소 매출 원장 생성은 결제 모듈의 리스너가
 * 이벤트를 구독해서 처리한다. Return 도메인이 결제 내부 구현(PaymentCancelService 등)을 몰라도 되게 하기 위한
 * 의도적인 분리다.
 */
@Service
public class CommerceReturnService {
    private final CommerceReturnRepository returns;
    private final CommerceOrderRepository orders;
    private final CommerceOrderItemRepository orderItems;
    private final CommerceDeliveryRepository deliveries;
    private final ProductVariantRepository variants;
    private final InventoryTransactionService inventoryTransactionService;
    private final ApplicationEventPublisher eventPublisher;
    private final com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository storeInventories;
    private final InventoryLedgerService inventoryLedgerService;

    public CommerceReturnService(CommerceReturnRepository returns, CommerceOrderRepository orders,
            CommerceOrderItemRepository orderItems, CommerceDeliveryRepository deliveries,
            ProductVariantRepository variants, InventoryTransactionService inventoryTransactionService,
            ApplicationEventPublisher eventPublisher,
            com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository storeInventories,
            InventoryLedgerService inventoryLedgerService) {
        this.returns = returns;
        this.orders = orders;
        this.orderItems = orderItems;
        this.deliveries = deliveries;
        this.variants = variants;
        this.inventoryTransactionService = inventoryTransactionService;
        this.eventPublisher = eventPublisher;
        this.storeInventories = storeInventories;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional
    public ReturnResponse requestReturn(ReturnCreateRequest request) {
        return requestReturn(request, null);
    }

    @Transactional
    public ReturnResponse requestReturn(ReturnCreateRequest request, Long storeId) {
        CommerceOrder order = orders.findById(request.orderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        requireStore(order, storeId);
        if (order.getOrderStatus() != OrderStatus.PAID && order.getOrderStatus() != OrderStatus.PARTIALLY_CANCELLED)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "결제가 완료된 주문만 반품을 접수할 수 있습니다.");
        if (request.deliveryId() != null) {
            CommerceDelivery delivery = deliveries.findById(request.deliveryId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
            if (!delivery.getOrderId().equals(request.orderId()))
                throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "선택한 배송이 이 주문의 배송이 아닙니다.");
            if (delivery.getStatus() != DeliveryStatus.DELIVERED)
                throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 완료된 상품만 반품을 접수할 수 있습니다.");
        }
        // 같은 범위(같은 deliveryId, 또는 배송 특정 없이 주문 전체)에 대해 이미 진행 중이거나 완료된 반품이
        // 있으면 또 접수할 수 없다 — 배송 상태(DELIVERED→RETURNED)만으로는 REQUESTED~INSPECTING 사이의
        // 중복 접수를 막지 못하고(완료 전까지는 배송이 계속 DELIVERED 상태다), deliveryId가 없는 "주문 전체
        // 반품"은 애초에 배송 상태 체크 자체가 없어서 별도로 막아야 한다. 반려(REJECTED)된 반품은 다시
        // 접수할 수 있게 제외한다.
        boolean hasActiveOverlap = returns.findAllByOrderIdOrderByIdDesc(request.orderId()).stream()
                .anyMatch(existing -> existing.getStatus() != ReturnStatus.REJECTED
                        && java.util.Objects.equals(existing.getDeliveryId(), request.deliveryId()));
        if (hasActiveOverlap)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, request.deliveryId() == null
                    ? "이미 이 주문 전체에 대한 반품이 접수되어 있습니다."
                    : "이미 이 배송 건에 대한 반품이 접수되어 있습니다.");
        CommerceReturn saved = returns.save(CommerceReturn.builder()
                .orderId(request.orderId())
                .deliveryId(request.deliveryId())
                .reason(request.reason().trim())
                .responsibility(request.responsibility())
                .returnShippingFee(request.returnShippingFee())
                .build());
        return toResponse(saved, Map.of(order.getId(), order));
    }

    @Transactional
    public ReturnResponse startInspection(Long returnId) {
        return startInspection(returnId, null);
    }

    @Transactional
    public ReturnResponse startInspection(Long returnId, Long storeId) {
        CommerceReturn r = findOrThrow(returnId);
        requireStore(r.getOrderId(), storeId);
        r.startInspection();
        return toResponse(r, Map.of());
    }

    /** 검수 완료: 반품 대상 상품의 재고를 복원하고, 환불 금액을 확정한 뒤 반송 상태로 배송을 마감하고,
     * 마지막으로 "환불 확정" 이벤트를 발행한다. 부분배송/부분반품을 지원하기 위해 환불 금액은 주문 전체가 아니라
     * 이 반품이 걸린 상품들의 금액 합계로만 계산한다. */
    @Transactional
    public ReturnResponse completeInspection(Long returnId, BigDecimal returnShippingFeeOverride) {
        return completeInspection(returnId, returnShippingFeeOverride, null);
    }

    @Transactional
    public ReturnResponse completeInspection(Long returnId, BigDecimal returnShippingFeeOverride, Long storeId) {
        CommerceReturn r = findOrThrow(returnId);
        CommerceOrder order = orders.findById(r.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        requireStore(order, storeId);

        List<CommerceOrderItem> targetItems = resolveTargetItems(r);
        BigDecimal refundBase = targetItems.stream().map(CommerceOrderItem::getItemAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fee = r.getResponsibility() == ReturnResponsibility.CUSTOMER_FAULT
                ? (returnShippingFeeOverride != null ? returnShippingFeeOverride : r.getReturnShippingFee())
                : BigDecimal.ZERO;
        BigDecimal refundAmount = refundBase.subtract(fee);
        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) refundAmount = BigDecimal.ZERO;

        r.complete(returnShippingFeeOverride, refundAmount);

        // 검수를 통과한 상품만 재고로 되돌린다 — 반품 접수 시점이 아니라 여기서 복원하는 이유는 클래스 상단 주석 참고.
        // 이미 출고(shippedYn=true)된 상품은 현재재고가 실제로 빠져나간 상태이므로 재입고(RECEIPT)로 되돌리고,
        // 아직 출고 전(예약만 걸려 있던 상태)이면 물건이 나간 적이 없으므로 예약만 풀어준다(RELEASE) —
        // 이 경우 receiveStock()을 쓰면 나간 적 없는 재고가 이중으로 늘어나는 오류가 된다.
        Long fulfillmentStoreId = order.getStoreId();
        for (CommerceOrderItem item : targetItems) {
            if (item.getProductVariantId() == null) continue;
            Long variantId = item.getProductVariantId();
            var storeInv = fulfillmentStoreId == null ? java.util.Optional.<com.yeni.backoffice.core.commerce.entity.StoreVariantInventory>empty()
                    : storeInventories.findByStoreIdAndVariantId(fulfillmentStoreId, variantId);
            if (item.isShippedYn()) {
                // 출고된 상품 재입고 — 반품 LOT("RTN-...")로 발번해 유통기한 추적을 이어간다.
                if (storeInv.isPresent()) {
                    inventoryLedgerService.receive(fulfillmentStoreId, variantId, item.getQuantity(),
                            storeInv.get().getAverageUnitCost(), "RTN", null, null, "반품 재입고",
                            "반품 검수 완료에 따른 재입고: " + order.getOrderNo(), "RETURN", r.getId(), "SYSTEM");
                } else {
                    ProductVariant variant = variants.findByIdForUpdate(variantId)
                            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "SKU 정보를 찾을 수 없습니다."));
                    variant.receiveStock(item.getQuantity());
                    inventoryTransactionService.record(variant, InventoryTransactionType.RECEIPT, item.getQuantity(),
                            "반품 검수 완료에 따른 재입고: " + order.getOrderNo(), "RETURN", r.getId(), "SYSTEM");
                }
            } else {
                if (storeInv.isPresent()) {
                    inventoryLedgerService.release(fulfillmentStoreId, variantId, item.getQuantity(),
                            "반품 처리로 인한 예약 해제(출고 전): " + order.getOrderNo(), "RETURN", r.getId(), "SYSTEM");
                } else {
                    ProductVariant variant = variants.findByIdForUpdate(variantId)
                            .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "SKU 정보를 찾을 수 없습니다."));
                    variant.releaseReservation(item.getQuantity());
                    inventoryTransactionService.record(variant, InventoryTransactionType.RELEASE, item.getQuantity(),
                            "반품 처리로 인한 예약 해제(출고 전): " + order.getOrderNo(), "RETURN", r.getId(), "SYSTEM");
                }
            }
        }

        if (r.getDeliveryId() != null) {
            CommerceDelivery delivery = deliveries.findById(r.getDeliveryId())
                    .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
            delivery.markReturned(r.getReason());
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) > 0 && order.getPaymentId() != null) {
            eventPublisher.publishEvent(new ReturnCompletedEvent(
                    r.getId(), order.getId(), order.getOrderNo(), order.getPaymentId(), refundAmount,
                    "반품 환불(반품 #" + r.getId() + "): " + r.getReason()));
        }
        return toResponse(r, Map.of(order.getId(), order));
    }

    /** 환불(PG 취소) 처리 결과를 기록한다. 반드시 검수 완료 트랜잭션과 별개의 새 트랜잭션으로 커밋돼야 한다 —
     * 이 메서드를 호출하는 쪽(ReturnRefundEventListener)이 실패 케이스에서 자기 자신의 트랜잭션이 이미
     * rollback-only로 마킹된 상태일 수 있는데, REQUIRES_NEW는 그 상태와 무관하게 완전히 독립된 트랜잭션을
     * 새로 열기 때문에 실패 기록 자체는 항상 커밋된다. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markRefundSucceeded(Long returnId) {
        findOrThrow(returnId).markRefundSucceeded();
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void markRefundFailed(Long returnId, String reason) {
        findOrThrow(returnId).markRefundFailed(reason);
    }

    /** 환불(PG 취소) 실패 건을 재시도한다. cancelPayment는 반품ID 기반의 고정된 취소 요청 키(RETURN-{id})로
     * 멱등 처리되므로, 같은 이벤트를 다시 발행해도 이전 시도가 PG 취소까지 실제로 성공했었다면 중복 취소되지
     * 않고 그 결과를 그대로 돌려받는다 — 실패해서 재시도하는 게 맞는 상황(PG 응답 전 타임아웃 등)에서만
     * 진짜로 다시 시도된다. */
    @Transactional
    public ReturnResponse retryRefund(Long returnId) {
        return retryRefund(returnId, null);
    }

    @Transactional
    public ReturnResponse retryRefund(Long returnId, Long storeId) {
        CommerceReturn r = findOrThrow(returnId);
        requireStore(r.getOrderId(), storeId);
        r.markRefundPending();
        CommerceOrder order = orders.findById(r.getOrderId())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "주문을 찾을 수 없습니다."));
        eventPublisher.publishEvent(new ReturnCompletedEvent(r.getId(), order.getId(), order.getOrderNo(),
                order.getPaymentId(), r.getRefundAmount(), "반품 환불 재시도(반품 #" + r.getId() + "): " + r.getReason()));
        return toResponse(r, Map.of(order.getId(), order));
    }

    @Transactional
    public ReturnResponse reject(Long returnId, String rejectReason) {
        return reject(returnId, rejectReason, null);
    }

    @Transactional
    public ReturnResponse reject(Long returnId, String rejectReason, Long storeId) {
        CommerceReturn r = findOrThrow(returnId);
        requireStore(r.getOrderId(), storeId);
        r.reject(rejectReason);
        return toResponse(r, Map.of());
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> listByOrder(Long orderId) {
        return toResponses(returns.findAllByOrderIdOrderByIdDesc(orderId));
    }

    @Transactional(readOnly = true)
    public List<ReturnResponse> list(String status) { return list(status, null); }

    @Transactional(readOnly = true)
    public List<ReturnResponse> list(String status, Long storeId) {
        List<CommerceReturn> rows = StringUtils.hasText(status)
                ? returns.findByStatusOrderByIdDesc(parseStatus(status))
                : returns.findAllByOrderByIdDesc();
        if (storeId != null) {
            Set<Long> scopedOrderIds = orders.findByStoreIdOrderByIdDesc(storeId).stream().map(CommerceOrder::getId).collect(Collectors.toSet());
            rows = rows.stream().filter(row -> scopedOrderIds.contains(row.getOrderId())).toList();
        }
        return toResponses(rows);
    }

    /** 반품이 특정 배송(deliveryId)에 걸려 있으면 그 배송에 속한 상품만, 아니면(주소 없는 주문 등) 주문 전체
     * 상품을 반품 대상으로 본다. */
    private List<CommerceOrderItem> resolveTargetItems(CommerceReturn r) {
        List<CommerceOrderItem> all = orderItems.findByOrderIdOrderByIdAsc(r.getOrderId());
        if (r.getDeliveryId() == null) return all;
        return all.stream().filter(i -> r.getDeliveryId().equals(i.getDeliveryId()))
                .sorted(Comparator.comparing(CommerceOrderItem::getId)).toList();
    }

    private CommerceOrder requireStore(Long orderId, Long storeId) {
        CommerceOrder order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        return requireStore(order, storeId);
    }

    private CommerceOrder requireStore(CommerceOrder order, Long storeId) {
        if (storeId != null && !storeId.equals(order.getStoreId()))
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    private CommerceReturn findOrThrow(Long returnId) {
        return returns.findById(returnId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "반품 정보를 찾을 수 없습니다."));
    }

    private ReturnStatus parseStatus(String value) {
        try { return ReturnStatus.valueOf(value); }
        catch (IllegalArgumentException e) { throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "알 수 없는 반품 상태입니다: " + value); }
    }

    private List<ReturnResponse> toResponses(List<CommerceReturn> rows) {
        if (rows.isEmpty()) return List.of();
        Set<Long> orderIds = rows.stream().map(CommerceReturn::getOrderId).collect(Collectors.toSet());
        Map<Long, CommerceOrder> orderMap = orders.findAllById(orderIds).stream()
                .collect(Collectors.toMap(CommerceOrder::getId, Function.identity()));
        return rows.stream().map(r -> toResponse(r, orderMap)).toList();
    }

    private ReturnResponse toResponse(CommerceReturn r, Map<Long, CommerceOrder> orderMap) {
        CommerceOrder order = orderMap.get(r.getOrderId());
        if (order == null) order = orders.findById(r.getOrderId()).orElse(null);
        return ReturnResponse.from(r, order == null ? "-" : order.getOrderNo(), order == null ? "-" : order.getBuyerName(), order == null ? null : order.getStoreId());
    }
}
