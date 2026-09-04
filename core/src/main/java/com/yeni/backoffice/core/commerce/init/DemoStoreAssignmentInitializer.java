package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 포트폴리오 데모의 과거 결제·매출 데이터에도 주문의 매장 귀속을 일관되게 채운다. */
@Component
@Profile("fly | demo")
@Order(190)
public class DemoStoreAssignmentInitializer implements CommandLineRunner {
    private final CommerceStoreRepository stores;
    private final CommerceOrderRepository orders;
    private final PaymentTransactionRepository payments;
    private final SalesTransactionRepository sales;

    public DemoStoreAssignmentInitializer(CommerceStoreRepository stores,
                                          CommerceOrderRepository orders,
                                          PaymentTransactionRepository payments,
                                          SalesTransactionRepository sales) {
        this.stores = stores;
        this.orders = orders;
        this.payments = payments;
        this.sales = sales;
    }

    @Override
    @Transactional
    public void run(String... args) {
        Long fallbackStoreId = stores.findAllByOrderByIdAsc().stream().findFirst().map(store -> store.getId()).orElse(null);
        if (fallbackStoreId == null) return;

        Map<String, CommerceOrder> orderByNo = orders.findAll().stream()
                .collect(Collectors.toMap(CommerceOrder::getOrderNo, Function.identity(), (left, right) -> left));
        Map<Long, PaymentTransaction> paymentById = payments.findAll().stream()
                .collect(Collectors.toMap(PaymentTransaction::getId, Function.identity()));

        paymentById.values().stream().filter(payment -> payment.getStoreId() == null).forEach(payment -> {
            CommerceOrder order = orderByNo.get(payment.getOrderNo());
            payment.assignStore(order != null && order.getStoreId() != null ? order.getStoreId() : fallbackStoreId);
        });
        sales.findAll().stream().filter(transaction -> transaction.getStoreId() == null).forEach(transaction -> {
            PaymentTransaction payment = paymentById.get(transaction.getPaymentId());
            CommerceOrder order = orderByNo.get(transaction.getOrderNo());
            Long storeId = payment != null && payment.getStoreId() != null ? payment.getStoreId()
                    : order != null && order.getStoreId() != null ? order.getStoreId() : fallbackStoreId;
            transaction.assignStore(storeId);
        });
    }
}
