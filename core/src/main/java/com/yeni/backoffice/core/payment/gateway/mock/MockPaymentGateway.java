package com.yeni.backoffice.core.payment.gateway.mock;

import com.yeni.backoffice.core.payment.enums.PaymentStatus;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.gateway.PaymentGateway;
import com.yeni.backoffice.core.payment.gateway.command.PaymentApproveCommand;
import com.yeni.backoffice.core.payment.gateway.command.PaymentCancelCommand;
import com.yeni.backoffice.core.payment.gateway.command.PaymentQueryCommand;
import com.yeni.backoffice.core.payment.gateway.result.PaymentApproveResult;
import com.yeni.backoffice.core.payment.gateway.result.PaymentCancelResult;
import com.yeni.backoffice.core.payment.gateway.result.PaymentQueryResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PgProvider provider() {
        return PgProvider.MOCK;
    }

    @Override
    public PaymentApproveResult approve(PaymentApproveCommand command) {
        String orderNo = command.orderNo().toUpperCase();
        if (orderNo.contains("UNKNOWN")) {
            return new PaymentApproveResult(provider(), false, PaymentStatus.APPROVE_UNKNOWN, null, "U000", "Mock approve result unknown.", null, true);
        }
        // 시나리오 데모 전용 마커. 실제 PG 응답코드 체계를 흉내낸 것일 뿐 진짜 PG와는 무관하다.
        if (orderNo.contains("METHODERR")) {
            return new PaymentApproveResult(provider(), false, PaymentStatus.APPROVE_FAILED, null, "9996", "지원하지 않는 결제수단입니다.", null, false);
        }
        if (orderNo.contains("CARDLIMIT")) {
            return new PaymentApproveResult(provider(), false, PaymentStatus.APPROVE_FAILED, null, "9995", "카드 한도를 초과했습니다.", null, false);
        }
        if (orderNo.contains("FAIL")) {
            return new PaymentApproveResult(provider(), false, PaymentStatus.APPROVE_FAILED, null, "9999", "Mock approve failure.", null, false);
        }
        String tid = "MOCK-" + UUID.nameUUIDFromBytes((command.orderNo() + command.idempotencyKey()).getBytes());
        return new PaymentApproveResult(provider(), true, PaymentStatus.APPROVED, tid, "0000", "Mock approve success.", LocalDateTime.now(), false);
    }

    @Override
    public PaymentCancelResult cancel(PaymentCancelCommand command) {
        if (command.idempotencyKey().toUpperCase().contains("UNKNOWN")) {
            return new PaymentCancelResult(provider(), false, PaymentStatus.CANCEL_UNKNOWN, "U000", "Mock cancel result unknown.", true);
        }
        if (command.idempotencyKey().toUpperCase().contains("FAIL")) {
            return new PaymentCancelResult(provider(), false, PaymentStatus.CANCEL_FAILED, "9999", "Mock cancel failure.", false);
        }
        return new PaymentCancelResult(provider(), true, PaymentStatus.CANCELED, "0000", "Mock cancel success.", false);
    }

    @Override
    public PaymentQueryResult query(PaymentQueryCommand command) {
        String orderNo = command.orderNo() == null ? "" : command.orderNo().toUpperCase();
        // "STUCK" 마커가 붙은 건은 재조회해도 여전히 결과 불명 — 운영자 판단이 필요한 케이스.
        if (orderNo.contains("STUCK")) {
            return new PaymentQueryResult(provider(), false, PaymentStatus.APPROVE_UNKNOWN, command.tid(), "U000", "Mock query still unknown.");
        }
        // 대부분의 timeout·응답유실은 실제로는 승인이 완료돼 있다 — 재조회로 확정된다.
        return new PaymentQueryResult(provider(), true, PaymentStatus.APPROVED, command.tid(), "0000", "Mock query confirmed approved.");
    }
}
