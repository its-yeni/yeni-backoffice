package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShipmentServiceTest {

    @Mock private CommerceOrderRepository orderRepository;
    @Mock private CommerceOrderItemRepository itemRepository;
    @Mock private ProductVariantRepository variantRepository;
    @Mock private InventoryTransactionService inventoryTransactionService;
    @Mock private StoreVariantInventoryRepository storeInventoryRepository;

    @Test
    void listPending_filtersPaidOrdersBySelectedStore() {
        CommerceOrder selectedStoreOrder = paidOrder(101L, 1L, "ORDER-STORE-1");
        CommerceOrder otherStoreOrder = paidOrder(202L, 2L, "ORDER-STORE-2");
        CommerceOrderItem selectedItem = orderItem(selectedStoreOrder.getId());

        when(orderRepository.findByOrderStatus(OrderStatus.PAID))
                .thenReturn(List.of(selectedStoreOrder, otherStoreOrder));
        when(itemRepository.findByOrderIdInAndShippedYnFalseAndProductVariantIdIsNotNullOrderByIdAsc(List.of(101L)))
                .thenReturn(List.of(selectedItem));
        when(orderRepository.findAllById(List.of(101L))).thenReturn(List.of(selectedStoreOrder));

        ShipmentService service = new ShipmentService(orderRepository, itemRepository, variantRepository,
                inventoryTransactionService, storeInventoryRepository);

        var result = service.listPending(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).orderId()).isEqualTo(101L);
        assertThat(result.get(0).orderNo()).isEqualTo("ORDER-STORE-1");
        verify(itemRepository)
                .findByOrderIdInAndShippedYnFalseAndProductVariantIdIsNotNullOrderByIdAsc(List.of(101L));
    }

    @Test
    void completeShipment_rejectsOrderOwnedByAnotherStore() {
        CommerceOrder otherStoreOrder = paidOrder(202L, 2L, "ORDER-STORE-2");
        CommerceOrderItem item = orderItem(otherStoreOrder.getId());
        when(itemRepository.findByIdForUpdate(1001L)).thenReturn(java.util.Optional.of(item));
        when(orderRepository.findById(otherStoreOrder.getId())).thenReturn(java.util.Optional.of(otherStoreOrder));

        ShipmentService service = new ShipmentService(orderRepository, itemRepository, variantRepository,
                inventoryTransactionService, storeInventoryRepository);

        assertThatThrownBy(() -> service.completeShipment(1001L, 1L))
                .isInstanceOf(com.yeni.backoffice.core.common.exception.NotFoundException.class);
        verifyNoInteractions(variantRepository, inventoryTransactionService, storeInventoryRepository);
    }

    private CommerceOrder paidOrder(Long id, Long storeId, String orderNo) {
        return CommerceOrder.builder()
                .id(id)
                .storeId(storeId)
                .orderNo(orderNo)
                .buyerName("운영 테스트")
                .productName("테스트 상품")
                .productAmount(BigDecimal.valueOf(10_000))
                .deliveryFee(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .payableAmount(BigDecimal.valueOf(10_000))
                .paidAmount(BigDecimal.valueOf(10_000))
                .cancelledAmount(BigDecimal.ZERO)
                .orderStatus(OrderStatus.PAID)
                .build();
    }

    private CommerceOrderItem orderItem(Long orderId) {
        return CommerceOrderItem.createConfigured(orderId, 301L, "TEST-001", "테스트 상품", "테스트분류",
                BigDecimal.valueOf(10_000), BigDecimal.ZERO, 1, "기본", "", false, 501L);
    }
}
