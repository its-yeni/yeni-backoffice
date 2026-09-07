package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.dto.CommerceDeliveryDtos.DeliveryResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderSummaryResponse;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.scope.OperationalScope;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CommerceOrderQueryService {
    private final CommerceOrderRepository orders;
    private final CommerceOrderItemRepository orderItems;
    private final CommerceDeliveryService deliveries;

    public CommerceOrderQueryService(
            CommerceOrderRepository orders,
            CommerceOrderItemRepository orderItems,
            CommerceDeliveryService deliveries) {
        this.orders = orders;
        this.orderItems = orderItems;
        this.deliveries = deliveries;
    }

    public List<CommerceOrderResponse> getOrders() {
        return getOrdersInScope(OperationalScope.all());
    }

    public List<CommerceOrderResponse> getOrders(Long storeId) {
        return getOrdersInScope(storeId == null
                ? OperationalScope.all()
                : OperationalScope.stores(null, storeId, Set.of(storeId)));
    }

    public List<CommerceOrderResponse> getOrdersInScope(OperationalScope scope) {
        List<CommerceOrder> orderRows = scope.unrestricted()
                ? orders.findAllByOrderByIdDesc()
                : orders.findByStoreIdInOrderByIdDesc(scope.storeIds());
        if (orderRows.isEmpty()) return List.of();

        List<CommerceOrderItem> itemRows = orderItems.findByOrderIdInOrderByOrderIdAscIdAsc(
                orderRows.stream().map(CommerceOrder::getId).toList());
        Map<Long, List<CommerceOrderItem>> itemsByOrder = itemRows.stream()
                .collect(Collectors.groupingBy(CommerceOrderItem::getOrderId));
        Map<Long, DeliveryResponse> deliveryByOrder = deliveries.findFirstByOrders(orderRows, itemRows);

        return orderRows.stream()
                .map(order -> CommerceOrderResponse.from(
                        order,
                        itemsByOrder.getOrDefault(order.getId(), List.of()),
                        deliveryByOrder.get(order.getId())))
                .toList();
    }

    public CommerceOrderResponse getOrder(Long orderId) {
        CommerceOrder order = orders.findById(orderId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.ORDER_NOT_FOUND));
        return CommerceOrderResponse.from(
                order,
                orderItems.findByOrderIdOrderByIdAsc(orderId),
                deliveries.findByOrderId(orderId));
    }

    public CommerceOrderSummaryResponse getSummary(Long storeId) {
        return CommerceOrderSummaryResponse.from(storeId == null
                ? orders.findAll()
                : orders.findByStoreIdOrderByIdDesc(storeId));
    }

    public CommerceOrderSummaryResponse getSummaryInScope(OperationalScope scope) {
        return CommerceOrderSummaryResponse.from(scope.unrestricted()
                ? orders.findAll()
                : orders.findByStoreIdInOrderByIdDesc(scope.storeIds()));
    }
}
