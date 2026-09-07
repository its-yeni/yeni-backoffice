package com.yeni.backoffice.core.commerce.service;

import com.yeni.backoffice.core.commerce.entity.CommerceStore;
import com.yeni.backoffice.core.commerce.entity.Product;
import com.yeni.backoffice.core.commerce.entity.ProductOptionValue;
import com.yeni.backoffice.core.commerce.entity.ProductVariant;
import com.yeni.backoffice.core.commerce.enums.InventoryTransactionType;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OrderInventoryReservationService {
    private final InventoryLedgerService inventoryLedgerService;
    private final InventoryTransactionService inventoryTransactionService;

    public OrderInventoryReservationService(InventoryLedgerService inventoryLedgerService,
            InventoryTransactionService inventoryTransactionService) {
        this.inventoryLedgerService = inventoryLedgerService;
        this.inventoryTransactionService = inventoryTransactionService;
    }

    public void reserveSellable(Product product, List<ProductOptionValue> selectedOptions,
            ProductVariant variant, CommerceStore fulfillmentStore, int quantity) {
        if (variant == null) {
            product.decreaseStock(quantity);
            selectedOptions.forEach(option -> option.decreaseStock(quantity));
        } else if (fulfillmentStore == null) {
            variant.reserve(quantity);
        }
    }

    public void reserveAddon(Product addon, int quantity) {
        addon.decreaseStock(quantity);
    }

    public void recordVariantReservations(Long orderId, CommerceStore fulfillmentStore,
            List<VariantReservation> reservations) {
        for (VariantReservation reservation : reservations) {
            if (fulfillmentStore != null) {
                inventoryLedgerService.reserve(fulfillmentStore.getId(), reservation.variant().getId(),
                        reservation.quantity(), "주문 생성에 따른 재고 예약", "ORDER", orderId, "SYSTEM");
            } else {
                inventoryTransactionService.record(reservation.variant(), InventoryTransactionType.RESERVE,
                        reservation.quantity(), "주문 생성에 따른 재고 예약", "ORDER", orderId, "SYSTEM");
            }
        }
    }

    public record VariantReservation(ProductVariant variant, int quantity) {}
}
