package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.payment.entity.PaymentTransaction;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데모: 승인번호·카드사·카드 마스킹이 비어 있는 기존 결제 거래에 카드 상세를 채운다.
 * 새 결제는 {@code PaymentApproveService} 에서 {@code enrichCardDetail()} 로 이미 채워진다.
 */
@Component
@Profile("fly | demo")
@Order(207)
public class DemoPaymentCardDetailInitializer implements CommandLineRunner {

    private final PaymentTransactionRepository payments;

    public DemoPaymentCardDetailInitializer(PaymentTransactionRepository payments) {
        this.payments = payments;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (PaymentTransaction p : payments.findAll()) {
            if (p.getApprovalNo() == null && "CARD".equalsIgnoreCase(p.getPaymentMethod())) {
                p.enrichCardDetail();
            }
        }
    }
}
