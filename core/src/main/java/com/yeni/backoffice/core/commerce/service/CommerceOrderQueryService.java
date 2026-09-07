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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

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

    public CommerceOrderResponse getOrderInStore(Long orderId,Long storeId){
        CommerceOrderResponse response=getOrder(orderId);
        if(storeId==null||!storeId.equals(response.storeId()))
            throw new NotFoundException(ErrorCode.ORDER_NOT_FOUND,"해당 매장의 주문을 찾을 수 없습니다.");
        return response;
    }

    public OrderPage getOrdersInStore(Long storeId,Integer requestedPage,Integer requestedSize){
        int page=Math.max(requestedPage==null?0:requestedPage,0);
        int size=Math.min(Math.max(requestedSize==null?30:requestedSize,1),100);
        Page<CommerceOrder> rows=orders.findByStoreIdOrderByIdDesc(storeId,PageRequest.of(page,size));
        List<CommerceOrder> orderRows=rows.getContent();
        if(orderRows.isEmpty())return new OrderPage(page,size,rows.getTotalElements(),rows.getTotalPages(),List.of());
        List<CommerceOrderItem> itemRows=orderItems.findByOrderIdInOrderByOrderIdAscIdAsc(orderRows.stream().map(CommerceOrder::getId).toList());
        Map<Long,List<CommerceOrderItem>> itemsByOrder=itemRows.stream().collect(Collectors.groupingBy(CommerceOrderItem::getOrderId));
        Map<Long,DeliveryResponse> deliveryByOrder=deliveries.findFirstByOrders(orderRows,itemRows);
        List<CommerceOrderResponse> content=orderRows.stream().map(order->CommerceOrderResponse.from(order,
                itemsByOrder.getOrDefault(order.getId(),List.of()),deliveryByOrder.get(order.getId()))).toList();
        return new OrderPage(page,size,rows.getTotalElements(),rows.getTotalPages(),content);
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

    public record OrderPage(int page,int size,long totalElements,int totalPages,List<CommerceOrderResponse> content){}
}
