package com.yeni.backoffice.core.payment.support;

import com.yeni.backoffice.core.payment.entity.AuditLog;
import com.yeni.backoffice.core.payment.entity.PgApiLog;
import com.yeni.backoffice.core.payment.enums.LogResultStatus;
import com.yeni.backoffice.core.payment.enums.PaymentEventType;
import com.yeni.backoffice.core.payment.enums.PgApiType;
import com.yeni.backoffice.core.payment.enums.PgCompany;
import com.yeni.backoffice.core.payment.enums.PgProvider;
import com.yeni.backoffice.core.payment.repository.AuditLogRepository;
import com.yeni.backoffice.core.payment.repository.PgApiLogRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.slf4j.MDC;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class PaymentAuditHelper {

    private final PgApiLogRepository pgApiLogRepository;
    private final AuditLogRepository auditLogRepository;

    public PaymentAuditHelper(PgApiLogRepository pgApiLogRepository, AuditLogRepository auditLogRepository) {
        this.pgApiLogRepository = pgApiLogRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public PgApiLog savePgLog(Long paymentId, String orderNo, PgProvider pgProvider,
                               PaymentEventType eventType, PgApiType apiType,
                               String idempotencyKey, String requestBody,
                               LogResultStatus status, String message) {
        return pgApiLogRepository.save(PgApiLog.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .orderNo(orderNo)
                .pgCompany(PgCompany.valueOf(pgProvider.name().equals("MOCK") ? "INICIS" : pgProvider.name()))
                .pgProvider(pgProvider)
                .eventType(eventType)
                .apiType(apiType)
                .idempotencyKey(idempotencyKey)
                .requestBody(requestBody)
                .resultStatus(status)
                .resultMessage(message)
                .httpStatus(200)
                .successYn(LogResultStatus.SUCCESS.equals(status))
                .loggedAt(LocalDateTime.now())
                .build());
    }

    public PgApiLog savePgLog(Long paymentId, String orderNo, PgApiType apiType,
                               String requestBody, LogResultStatus status, String message) {
        return pgApiLogRepository.save(PgApiLog.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .orderNo(orderNo)
                .pgCompany(PgCompany.INICIS)
                .apiType(apiType)
                .requestBody(requestBody)
                .resultStatus(status)
                .resultMessage(message)
                .loggedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 이미 최종 상태(성공/실패)가 결정된 PG API 로그를 한 번의 INSERT로 독립 트랜잭션에 남긴다.
     * 호출부의 비즈니스 트랜잭션이 이후 예외로 롤백되더라도 이 로그는 남아야 하기 때문에
     * REQUIRES_NEW로 분리한다. "REQUESTED로 먼저 저장 후 같은 트랜잭션에서 complete()로 갱신"하는
     * 기존 패턴은, 실패를 이유로 예외를 던져 트랜잭션이 롤백되는 경로에서는 INSERT와 UPDATE가
     * 통째로 사라지는 문제가 있어 이 메서드로 대체한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PgApiLog recordPgApiLog(Long paymentId, String orderNo, PgProvider pgProvider,
                                    PaymentEventType eventType, PgApiType apiType,
                                    String idempotencyKey, String requestBody, String responseBody,
                                    LogResultStatus status, String message, String tid) {
        return pgApiLogRepository.save(PgApiLog.builder()
                .requestId(UUID.randomUUID().toString())
                .paymentId(paymentId)
                .orderNo(orderNo)
                .pgCompany(PgCompany.valueOf(pgProvider.name().equals("MOCK") ? "INICIS" : pgProvider.name()))
                .pgProvider(pgProvider)
                .eventType(eventType)
                .apiType(apiType)
                .idempotencyKey(idempotencyKey)
                .requestBody(requestBody)
                .responseBody(responseBody)
                .tid(tid)
                .resultStatus(status)
                .resultMessage(message)
                .httpStatus(200)
                .successYn(LogResultStatus.SUCCESS.equals(status))
                .loggedAt(LocalDateTime.now())
                .build());
    }

    /**
     * 감사 로그는 추적 대상 액션이 실패해도 "실패했다"는 사실 자체가 남아야 의미가 있으므로
     * 항상 별도 트랜잭션(REQUIRES_NEW)으로 커밋한다. 호출부 트랜잭션의 롤백에 영향받지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void saveAudit(String domainType, String actionType, String referenceKey, String description) {
        auditLogRepository.save(AuditLog.builder()
                .domainType(domainType)
                .actionType(actionType)
                .referenceKey(referenceKey)
                .description(description)
                .actor("SYSTEM")
                .resultStatus(actionType != null && actionType.contains("FAILED") ? "FAILED" : "SUCCESS")
                .requestId(MDC.get("requestId"))
                .loggedAt(LocalDateTime.now())
                .build());
    }
}
