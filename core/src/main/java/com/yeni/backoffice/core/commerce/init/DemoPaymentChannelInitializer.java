package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
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

/**
 * 포트폴리오 데모: 결제 채널(WEB / POS)을 섞는다.
 * <p>실제 POS 승인 경로({@code PosPaymentService})를 데모에서 다 태우긴 번거로워서, 이미 생성된
 * 결제 중 약 30%를 매장 POS 결제로 재분류한다(주문·결제·매출 원장에 일관되게 반영).
 * 그 중 일부는 현금 결제로 둔다 — 온라인엔 없는 축을 화면에서 보여주기 위함.
 */
@Component
@Profile("fly | demo")
@Order(206)
public class DemoPaymentChannelInitializer implements CommandLineRunner {

    private final CommerceOrderRepository orders;
    private final PaymentTransactionRepository payments;
    private final SalesTransactionRepository sales;

    public DemoPaymentChannelInitializer(CommerceOrderRepository orders,
                                         PaymentTransactionRepository payments,
                                         SalesTransactionRepository sales) {
        this.orders = orders;
        this.payments = payments;
        this.sales = sales;
    }

    private static boolean isPos(String orderNo) {
        return orderNo != null && Math.floorMod(orderNo.hashCode(), 10) < 3;
    }

    private static boolean isCash(String orderNo) {
        return orderNo != null && Math.floorMod(orderNo.hashCode() / 7, 10) < 3; // POS 중 약 30%
    }

    @Override
    @Transactional
    public void run(String... args) {
        Map<String, CommerceOrder> orderByNo = orders.findAll().stream()
                .collect(Collectors.toMap(CommerceOrder::getOrderNo, Function.identity(), (a, b) -> a));

        for (PaymentTransaction payment : payments.findAll()) {
            String orderNo = payment.getOrderNo();
            if (!isPos(orderNo)) continue;
            payment.assignChannel("POS");
            if (isCash(orderNo)) {
                payment.updatePaymentMethod("CASH");
            }
            CommerceOrder order = orderByNo.get(orderNo);
            if (order != null) {
                order.assignChannel("POS");
            }
        }

        sales.findAll().forEach(tx -> {
            if (isPos(tx.getOrderNo())) {
                tx.assignChannel("POS");
                if (isCash(tx.getOrderNo())) {
                    tx.updatePaymentMethod("CASH");
                }
            }
        });
    }
}
