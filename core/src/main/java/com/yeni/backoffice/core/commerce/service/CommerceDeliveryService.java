package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.BulkDispatchResult;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.BulkDispatchRow;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryAddressRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.payment.service.SalesLedgerService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 주문의 배송지 등록과 배송 상태 진행(준비 → 배송 중 → 배송 완료 → 반송)을 담당한다.
 * 재고 출고(ShipmentService, SKU 재고 관점)와는 별도 도메인이다 — 주문 하나가 여러 SKU를 담고 있어도
 * 배송은 보통 박스 하나로 나가므로 주문 단위로 관리하되, 필요하면 부분배송(주문 상품 일부만 먼저 발송)도 지원한다.
 */
@Service
public class CommerceDeliveryService {
    private final CommerceDeliveryRepository deliveries;
    private final CommerceOrderRepository orders;
    private final CommerceOrderItemRepository orderItems;
    private final SalesLedgerService salesLedgerService;

    public CommerceDeliveryService(CommerceDeliveryRepository deliveries, CommerceOrderRepository orders,
            CommerceOrderItemRepository orderItems, SalesLedgerService salesLedgerService) {
        this.deliveries = deliveries;
        this.orders = orders;
        this.orderItems = orderItems;
        this.salesLedgerService = salesLedgerService;
    }

    /** 배송지 정보가 실제로 입력된 경우에만 배송 레코드를 만든다 — 주소 없이 주문을 만드는 기존 흐름(Mock 주문 생성 등)을 막지 않기 위해서다.
     * 주문 상품 전체를 이 배송에 연결하고, 나중에 필요하면 {@link #splitDelivery}로 일부를 떼어낼 수 있다. */
    @Transactional
    public void createForOrder(Long orderId, String fallbackReceiverName, String fallbackReceiverPhone, DeliveryAddressRequest request) {
        if (request == null || !StringUtils.hasText(request.address1())) return;
        if (!deliveries.findAllByOrderIdOrderByIdAsc(orderId).isEmpty()) return;
        CommerceDelivery saved = deliveries.save(CommerceDelivery.builder()
                .orderId(orderId)
                .receiverName(StringUtils.hasText(request.receiverName()) ? request.receiverName().trim() : fallbackReceiverName)
                .receiverPhone(StringUtils.hasText(request.receiverPhone()) ? request.receiverPhone().trim() : fallbackReceiverPhone)
                .zipCode(trimToNull(request.zipCode()))
                .address1(request.address1().trim())
                .address2(trimToNull(request.address2()))
                .deliveryRequest(trimToNull(request.deliveryRequest()))
                .status(DeliveryStatus.PREPARING)
                .build());
        List<CommerceOrderItem> items = orderItems.findByOrderIdOrderByIdAsc(orderId);
        items.forEach(item -> item.assignDelivery(saved.getId()));
    }

    /** 주문 생성 직후처럼 "이 주문의 대표 배송" 하나만 필요할 때 쓴다 — 부분배송으로 여러 건이 된 뒤에는
     * 가장 먼저 만들어진 배송을 대표로 반환한다. 전체를 봐야 하면 {@link #listByOrder}를 쓴다. */
    @Transactional(readOnly = true)
    public DeliveryResponse findByOrderId(Long orderId) {
        return deliveries.findAllByOrderIdOrderByIdAsc(orderId).stream().findFirst().map(d -> toResponse(d, Map.of())).orElse(null);
    }

    @Transactional(readOnly = true)
    public Map<Long, DeliveryResponse> findFirstByOrders(
            List<CommerceOrder> orderRows,
            List<CommerceOrderItem> itemRows) {
        if (orderRows.isEmpty()) return Map.of();
        Map<Long, CommerceOrder> orderMap = orderRows.stream()
                .collect(Collectors.toMap(CommerceOrder::getId, Function.identity()));
        Map<Long, List<CommerceOrderItem>> itemsByOrder = itemRows.stream()
                .collect(Collectors.groupingBy(CommerceOrderItem::getOrderId));
        return deliveries.findByOrderIdIn(orderMap.keySet()).stream()
                .sorted(java.util.Comparator.comparing(CommerceDelivery::getId))
                .collect(Collectors.toMap(
                        CommerceDelivery::getOrderId,
                        delivery -> {
                            CommerceOrder order = orderMap.get(delivery.getOrderId());
                            int unshippedItemCount = (int) itemsByOrder.getOrDefault(delivery.getOrderId(), List.of()).stream()
                                    .filter(item -> delivery.getId().equals(item.getDeliveryId()) && !item.isShippedYn())
                                    .count();
                            return DeliveryResponse.from(delivery, order.getOrderNo(), order.getBuyerName(),
                                    unshippedItemCount, order.getStoreId());
                        },
                        (first, ignored) -> first));
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> listByOrder(Long orderId) {
        return deliveries.findAllByOrderIdOrderByIdAsc(orderId).stream().map(d -> toResponse(d, Map.of())).toList();
    }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(String status) { return list(status, null); }

    @Transactional(readOnly = true)
    public List<DeliveryResponse> list(String status, Long storeId) {
        List<CommerceDelivery> rows = StringUtils.hasText(status)
                ? deliveries.findByStatusOrderByIdDesc(parseStatus(status))
                : deliveries.findAllByOrderByIdDesc();
        if (rows.isEmpty()) return List.of();
        List<Long> orderIds = rows.stream().map(CommerceDelivery::getOrderId).distinct().toList();
        Map<Long, CommerceOrder> orderMap = orders.findAllById(orderIds)
                .stream().collect(Collectors.toMap(CommerceOrder::getId, Function.identity()));
        Map<Long, List<CommerceOrderItem>> itemsByOrder = orderItems
                .findByOrderIdInOrderByOrderIdAscIdAsc(orderIds).stream()
                .collect(Collectors.groupingBy(CommerceOrderItem::getOrderId));
        return rows.stream().filter(d -> {
            CommerceOrder order = orderMap.get(d.getOrderId());
            return storeId == null || (order != null && storeId.equals(order.getStoreId()));
        }).map(d -> toResponse(d, orderMap, itemsByOrder)).toList();
    }

    @Transactional
    public DeliveryResponse dispatch(Long deliveryId, String carrier, String trackingNumber) {
        return dispatch(deliveryId, carrier, trackingNumber, null);
    }

    @Transactional
    public DeliveryResponse dispatch(Long deliveryId, String carrier, String trackingNumber, Long storeId) {
        CommerceDelivery delivery = deliveries.findById(deliveryId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
        requireStore(delivery.getOrderId(), storeId);
        if (!StringUtils.hasText(carrier) || !StringUtils.hasText(trackingNumber))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "택배사와 운송장 번호를 모두 입력해 주세요.");
        delivery.dispatch(carrier, trackingNumber);
        return toResponse(delivery, Map.of());
    }

    /** 송장 일괄등록. 행 하나가 실패해도 나머지 행은 계속 처리하고, 행별 성공/실패를 각자 돌려준다 —
     * 실무에서 엑셀 200줄을 올렸는데 1줄 오류로 전체가 롤백되면 그게 더 곤란하다. 주문번호로 배송을 찾을 때,
     * 부분배송으로 그 주문에 "배송 준비" 상태인 건이 2개 이상이면 어느 걸 발송하라는 건지 특정할 수 없으므로
     * 실패로 남기고 개별 화면에서 처리하게 한다. */
    @Transactional
    public List<BulkDispatchResult> bulkDispatch(List<BulkDispatchRow> rows) {
        return bulkDispatch(rows, null);
    }

    @Transactional
    public List<BulkDispatchResult> bulkDispatch(List<BulkDispatchRow> rows, Long storeId) {
        List<BulkDispatchResult> results = new java.util.ArrayList<>();
        for (BulkDispatchRow row : rows) {
            String orderNo = row.orderNo() == null ? "" : row.orderNo().trim();
            try {
                CommerceOrder order = orders.findByOrderNo(orderNo)
                        .orElseThrow(() -> new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "주문번호를 찾을 수 없습니다."));
                if (storeId != null && !storeId.equals(order.getStoreId()))
                    throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND);
                List<CommerceDelivery> preparing = deliveries.findAllByOrderIdOrderByIdAsc(order.getId()).stream()
                        .filter(d -> d.getStatus() == DeliveryStatus.PREPARING).toList();
                if (preparing.isEmpty())
                    throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 준비 상태인 배송 건이 없습니다.");
                if (preparing.size() > 1)
                    throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송 준비 상태인 배송 건이 여러 건입니다. 개별 화면에서 처리해 주세요.");
                dispatch(preparing.get(0).getId(), row.carrier(), row.trackingNumber(), storeId);
                results.add(new BulkDispatchResult(orderNo, true, "배송을 시작했습니다."));
            } catch (Exception e) {
                results.add(new BulkDispatchResult(orderNo, false, e.getMessage()));
            }
        }
        return results;
    }

    @Transactional
    public DeliveryResponse complete(Long deliveryId) {
        return complete(deliveryId, null);
    }

    @Transactional
    public DeliveryResponse complete(Long deliveryId, Long storeId) {
        CommerceDelivery delivery = deliveries.findById(deliveryId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
        requireStore(delivery.getOrderId(), storeId);
        delivery.complete();
        maybeConfirmPurchase(delivery.getOrderId(), delivery.getDeliveredAt());
        return toResponse(delivery, Map.of());
    }

    /**
     * 주문의 배송이 모두 종결(DELIVERED 또는 RETURNED)됐고 그중 정상 배송이 하나라도 있으면 구매 확정 처리한다.
     * 반송(RETURNED)분의 환불은 반품 도메인이 이미 CANCEL 원장으로 처리하므로, 정상 배송분만 매출 확정한다.
     * 부분배송에서 아직 배송 준비/배송 중인 건이 남아 있으면 확정하지 않는다(멱등 — 이미 확정된 주문은 건너뜀).
     */
    private void maybeConfirmPurchase(Long orderId, LocalDateTime deliveredAt) {
        List<CommerceDelivery> all = deliveries.findAllByOrderIdOrderByIdAsc(orderId);
        if (all.isEmpty()) return;
        boolean anyInProgress = all.stream().anyMatch(d ->
                d.getStatus() == DeliveryStatus.PREPARING || d.getStatus() == DeliveryStatus.IN_TRANSIT);
        boolean anyDelivered = all.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED);
        if (anyInProgress || !anyDelivered) return;
        LocalDateTime confirmedAt = deliveredAt == null ? LocalDateTime.now() : deliveredAt;
        CommerceOrder order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        if (order.markPurchaseConfirmed(confirmedAt)) {
            salesLedgerService.confirmOrderSales(order.getOrderNo(), confirmedAt);
        }
    }

    /**
     * 운영자 수동 구매 확정 — 배송 완료 후 자동 확정(D+N) 대기 시간을 건너뛰고 즉시 매출을 확정한다.
     * <b>배송이 완료된 주문만</b> 확정할 수 있다(배송 안 된 주문은 확정 불가). 이미 확정됐으면 멱등.
     */
    @Transactional
    public void forceConfirmPurchase(String orderNo) {
        CommerceOrder order = orders.findByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        List<CommerceDelivery> all = deliveries.findAllByOrderIdOrderByIdAsc(order.getId());
        boolean deliveredAll = !all.isEmpty()
                && all.stream().allMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED || d.getStatus() == DeliveryStatus.RETURNED)
                && all.stream().anyMatch(d -> d.getStatus() == DeliveryStatus.DELIVERED);
        if (!deliveredAll)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,
                    "배송이 완료된 주문만 구매 확정할 수 있습니다. 현재 배송이 진행 중입니다.");
        LocalDateTime now = LocalDateTime.now();
        order.markPurchaseConfirmed(now);
        salesLedgerService.confirmOrderSales(orderNo, now);
    }

    /**
     * 스케줄러용 — 배송 완료 후 {@code threshold} 이전에 배송된, 아직 구매 확정되지 않은 주문을 일괄 확정한다.
     * 반품/취소 중인 주문은 {@link CommerceOrder#markPurchaseConfirmed}에서 걸러진다.
     */
    @Transactional
    public int autoConfirmAgedDeliveries(LocalDateTime threshold) {
        List<CommerceDelivery> delivered = deliveries.findByStatusOrderByIdDesc(DeliveryStatus.DELIVERED).stream()
                .filter(d -> d.getDeliveredAt() != null && d.getDeliveredAt().isBefore(threshold))
                .toList();
        int confirmed = 0;
        for (Long orderId : delivered.stream().map(CommerceDelivery::getOrderId).distinct().toList()) {
            List<CommerceDelivery> all = deliveries.findAllByOrderIdOrderByIdAsc(orderId);
            boolean anyInProgress = all.stream().anyMatch(d ->
                    d.getStatus() == DeliveryStatus.PREPARING || d.getStatus() == DeliveryStatus.IN_TRANSIT);
            if (anyInProgress) continue;
            LocalDateTime deliveredAt = all.stream()
                    .filter(d -> d.getStatus() == DeliveryStatus.DELIVERED && d.getDeliveredAt() != null)
                    .map(CommerceDelivery::getDeliveredAt).max(LocalDateTime::compareTo).orElse(LocalDateTime.now());
            CommerceOrder order = orders.findById(orderId).orElse(null);
            if (order == null) continue;
            if (order.markPurchaseConfirmed(deliveredAt)) {
                salesLedgerService.confirmOrderSales(order.getOrderNo(), deliveredAt);
                confirmed++;
            }
        }
        return confirmed;
    }

    /** 반송 처리. 반송된다고 재고나 정산을 자동으로 되돌리지는 않는다 — 그건 별도의 반품/환불 도메인이 결정할 일이고,
     * 여기서는 "배송이 반송 상태가 됐다"는 사실만 정확히 남긴다. */
    @Transactional
    public DeliveryResponse returnDelivery(Long deliveryId, String reason) {
        CommerceDelivery delivery = deliveries.findById(deliveryId).orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "배송 정보를 찾을 수 없습니다."));
        if (!StringUtils.hasText(reason))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "반송 사유를 입력해 주세요.");
        delivery.markReturned(reason.trim());
        return toResponse(delivery, Map.of());
    }

    /** 부분배송 분리: 지정한 주문 상품들을 원래 배송에서 떼어내, 같은 주소로 나가는 새 배송 건을 만든다.
     * 이미 출고(shippedYn)됐거나 원래 배송이 이미 발송(PREPARING이 아님)된 상품은 분리할 수 없다 —
     * "아직 상자에 담기 전"인 상품만 다른 상자로 옮길 수 있다는 뜻이다. */
    @Transactional
    public DeliveryResponse splitDelivery(Long orderId, List<Long> orderItemIds) {
        if (orderItemIds == null || orderItemIds.isEmpty())
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "분리할 주문 상품을 1개 이상 선택해 주세요.");
        List<CommerceOrderItem> items = orderItems.findAllById(orderItemIds);
        if (items.size() != orderItemIds.size() || items.stream().anyMatch(item -> !orderId.equals(item.getOrderId())))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "선택한 주문 상품 정보가 올바르지 않습니다.");
        if (items.stream().anyMatch(CommerceOrderItem::isShippedYn))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "이미 출고된 주문 상품은 배송을 분리할 수 없습니다.");
        // 선택한 상품들이 지금 어느 배송에 속해 있는지는 상품 각각이 들고 있는 deliveryId로 판단한다(주문에 배송이
        // 이미 여러 건일 수 있으므로) — 서로 다른 배송에 걸쳐 있는 상품을 한 번에 분리하는 건 허용하지 않는다.
        Set<Long> sourceDeliveryIds = items.stream().map(CommerceOrderItem::getDeliveryId).collect(Collectors.toSet());
        if (sourceDeliveryIds.size() != 1 || sourceDeliveryIds.contains(null))
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "선택한 주문 상품은 모두 같은 배송에 속해 있어야 분리할 수 있습니다.");
        CommerceDelivery original = deliveries.findById(sourceDeliveryIds.iterator().next())
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "이 주문에는 등록된 배송지가 없습니다."));
        if (original.getStatus() != DeliveryStatus.PREPARING)
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "이미 발송된 배송은 나눌 수 없습니다.");
        if (items.size() == orderItems.findByOrderIdOrderByIdAsc(orderId).stream().filter(i -> original.getId().equals(i.getDeliveryId())).count())
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "배송에 속한 상품을 전부 선택하면 나눌 수 없습니다. 일부만 선택해 주세요.");

        CommerceDelivery split = deliveries.save(CommerceDelivery.builder()
                .orderId(orderId)
                .receiverName(original.getReceiverName()).receiverPhone(original.getReceiverPhone())
                .zipCode(original.getZipCode()).address1(original.getAddress1()).address2(original.getAddress2())
                .deliveryRequest(original.getDeliveryRequest())
                .status(DeliveryStatus.PREPARING)
                .build());
        items.forEach(item -> item.assignDelivery(split.getId()));
        return toResponse(split, Map.of());
    }

    private CommerceOrder requireStore(Long orderId, Long storeId) {
        CommerceOrder order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        if (storeId != null && !storeId.equals(order.getStoreId()))
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND);
        return order;
    }

    private DeliveryStatus parseStatus(String value) {
        try { return DeliveryStatus.valueOf(value); }
        catch (IllegalArgumentException e) { throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "알 수 없는 배송 상태입니다: " + value); }
    }

    private DeliveryResponse toResponse(CommerceDelivery d, Map<Long, CommerceOrder> orderMap) {
        CommerceOrder order = orderMap.get(d.getOrderId());
        if (order == null) order = orders.findById(d.getOrderId()).orElse(null);
        int unshippedItemCount = (int) orderItems.findByOrderIdOrderByIdAsc(d.getOrderId()).stream()
                .filter(item -> d.getId().equals(item.getDeliveryId()) && !item.isShippedYn())
                .count();
        return DeliveryResponse.from(d, order == null ? "-" : order.getOrderNo(), order == null ? "-" : order.getBuyerName(),
                unshippedItemCount, order == null ? null : order.getStoreId());
    }

    private DeliveryResponse toResponse(
            CommerceDelivery delivery,
            Map<Long, CommerceOrder> orderMap,
            Map<Long, List<CommerceOrderItem>> itemsByOrder) {
        CommerceOrder order = orderMap.get(delivery.getOrderId());
        int unshippedItemCount = (int) itemsByOrder.getOrDefault(delivery.getOrderId(), List.of()).stream()
                .filter(item -> delivery.getId().equals(item.getDeliveryId()) && !item.isShippedYn())
                .count();
        return DeliveryResponse.from(delivery, order == null ? "-" : order.getOrderNo(),
                order == null ? "-" : order.getBuyerName(), unshippedItemCount,
                order == null ? null : order.getStoreId());
    }

    private String trimToNull(String value) { return StringUtils.hasText(value) ? value.trim() : null; }
}
