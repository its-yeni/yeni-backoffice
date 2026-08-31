package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.entity.CommerceOrderItem;
import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderItemRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.ProductRepository;
import com.yeni.backoffice.core.commerce.repository.ProductOptionValueRepository;
import com.yeni.backoffice.core.commerce.repository.ProductVariantRepository;
import com.yeni.backoffice.core.commerce.repository.StoreVariantInventoryRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class CommerceOrderPaymentStateService {
    private final CommerceOrderRepository orderRepository;
    private final CommerceOrderItemRepository itemRepository;
    private final ProductRepository productRepository;
    private final ProductOptionValueRepository optionValueRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryTransactionService inventoryTransactionService;
    private final StoreVariantInventoryRepository storeInventoryRepository;
    private final InventoryLedgerService inventoryLedgerService;

    public CommerceOrderPaymentStateService(CommerceOrderRepository orderRepository,
                                            CommerceOrderItemRepository itemRepository,
                                            ProductRepository productRepository,
                                            ProductOptionValueRepository optionValueRepository,
                                            ProductVariantRepository variantRepository,
                                            InventoryTransactionService inventoryTransactionService,
                                            StoreVariantInventoryRepository storeInventoryRepository,
                                            InventoryLedgerService inventoryLedgerService) {
        this.orderRepository = orderRepository;
        this.itemRepository = itemRepository;
        this.productRepository = productRepository;
        this.optionValueRepository = optionValueRepository;
        this.variantRepository = variantRepository;
        this.inventoryTransactionService = inventoryTransactionService;
        this.storeInventoryRepository = storeInventoryRepository;
        this.inventoryLedgerService = inventoryLedgerService;
    }

    @Transactional(readOnly = true)
    public void validateApproval(String orderNo, BigDecimal amount) {
        orderRepository.findByOrderNo(orderNo).ifPresent(order -> {
            if (!OrderStatus.PENDING_PAYMENT.equals(order.getOrderStatus()))
                throw new ValidationBusinessException(ErrorCode.ORDER_PAYMENT_NOT_ALLOWED);
            if (amount == null || order.getPayableAmount().compareTo(amount) != 0)
                throw new ValidationBusinessException(ErrorCode.ORDER_PAYMENT_AMOUNT_MISMATCH);
        });
    }

    @Transactional
    public void markApproved(String orderNo, Long paymentId, String tid, String message) {
        orderRepository.findByOrderNoForUpdate(orderNo).ifPresent(order -> order.markApproved(paymentId, tid, message));
    }

    @Transactional
    public void markUnknown(String orderNo, Long paymentId, String tid, String message) {
        orderRepository.findByOrderNoForUpdate(orderNo).ifPresent(order -> order.markApproveUnknown(paymentId, tid, message));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndRestore(String orderNo, String message) {
        orderRepository.findByOrderNoForUpdate(orderNo).ifPresent(order -> {
            order.markPaymentFailed(message);
            restoreStock(order);
        });
    }

    @Transactional
    public void syncCancellation(String orderNo, BigDecimal totalCancelledAmount) {
        orderRepository.findByOrderNoForUpdate(orderNo).ifPresent(order -> {
            order.syncCancelledAmount(totalCancelledAmount);
            if (OrderStatus.CANCELLED.equals(order.getOrderStatus())) restoreStock(order);
        });
    }

    private void restoreStock(CommerceOrder order) {
        if (order.isStockRestored()) return;
        var items = itemRepository.findByOrderIdOrderByIdAsc(order.getId());
        Map<Long, Integer> quantities = items.stream().filter(item -> item.getProductVariantId() == null)
                .collect(Collectors.toMap(CommerceOrderItem::getProductId, CommerceOrderItem::getQuantity, Integer::sum));
        if (!quantities.isEmpty()) productRepository.findAllByIdForUpdate(quantities.keySet())
                .forEach(product -> product.increaseStock(quantities.get(product.getId())));
        Map<Long, Integer> optionQuantities = new HashMap<>();
        items.stream().filter(item -> !item.isAddonItem() && item.getProductVariantId() == null && item.getSelectedOptionValueIds() != null)
                .forEach(item -> Arrays.stream(item.getSelectedOptionValueIds().split(","))
                        .filter(id -> !id.isBlank()).map(Long::valueOf)
                        .forEach(id -> optionQuantities.merge(id, item.getQuantity(), Integer::sum)));
        if (!optionQuantities.isEmpty()) {
            optionValueRepository.findAllByIdForUpdate(optionQuantities.keySet())
                    .forEach(option -> option.increaseStock(optionQuantities.get(option.getId())));
        }
        Map<Long,Integer> variantQuantities=items.stream().filter(item->item.getProductVariantId()!=null).collect(Collectors.toMap(CommerceOrderItem::getProductVariantId,CommerceOrderItem::getQuantity,Integer::sum));
        if(!variantQuantities.isEmpty())variantRepository.findAllByIdForUpdate(variantQuantities.keySet()).forEach(v->{
            int qty=variantQuantities.get(v.getId());
            if(order.getStoreId()!=null&&storeInventoryRepository.findByStoreIdAndVariantId(order.getStoreId(),v.getId()).isPresent()){
                inventoryLedgerService.release(order.getStoreId(),v.getId(),qty,"결제 실패/취소에 따른 예약 해제","ORDER",order.getId(),"SYSTEM");
            } else {
                v.releaseReservation(qty);
                inventoryTransactionService.record(v,com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.RELEASE,qty,"결제 실패/취소에 따른 예약 해제","ORDER",order.getId(),"SYSTEM");
            }
        });
        order.markStockRestored();
    }
}
