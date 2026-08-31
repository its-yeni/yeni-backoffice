package com.yeni.backoffice.core.commerce.entity;

import com.yeni.backoffice.core.commerce.enums.OrderStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CommerceOrderTest {

    private CommerceOrder order(OrderStatus status) {
        return CommerceOrder.builder()
                .orderNo("ORD-1").buyerName("홍길동").productName("피자")
                .productAmount(BigDecimal.TEN).deliveryFee(BigDecimal.ZERO).discountAmount(BigDecimal.ZERO)
                .payableAmount(BigDecimal.TEN).paidAmount(BigDecimal.TEN).cancelledAmount(BigDecimal.ZERO)
                .orderStatus(status)
                .build();
    }

    @Test
    void markPurchaseConfirmed_fromPaid_transitionsAndReturnsTrue() {
        CommerceOrder o = order(OrderStatus.PAID);
        LocalDateTime at = LocalDateTime.now();

        boolean changed = o.markPurchaseConfirmed(at);

        assertThat(changed).isTrue();
        assertThat(o.getOrderStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
        assertThat(o.getPurchaseConfirmedAt()).isEqualTo(at);
    }

    @Test
    void markPurchaseConfirmed_fromPartiallyCancelled_isAllowed() {
        CommerceOrder o = order(OrderStatus.PARTIALLY_CANCELLED);
        assertThat(o.markPurchaseConfirmed(LocalDateTime.now())).isTrue();
        assertThat(o.getOrderStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
    }

    @Test
    void markPurchaseConfirmed_isIdempotent() {
        CommerceOrder o = order(OrderStatus.PAID);
        o.markPurchaseConfirmed(LocalDateTime.now());

        assertThat(o.markPurchaseConfirmed(LocalDateTime.now())).isFalse();
        assertThat(o.getOrderStatus()).isEqualTo(OrderStatus.PURCHASE_CONFIRMED);
    }

    @Test
    void markPurchaseConfirmed_cancelledOrPendingOrder_isRejected() {
        for (OrderStatus s : new OrderStatus[]{OrderStatus.CANCELLED, OrderStatus.PENDING_PAYMENT, OrderStatus.PAYMENT_FAILED}) {
            CommerceOrder o = order(s);
            assertThat(o.markPurchaseConfirmed(LocalDateTime.now())).as("status %s", s).isFalse();
            assertThat(o.getOrderStatus()).isEqualTo(s);
        }
    }
}
