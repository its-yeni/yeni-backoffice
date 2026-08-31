package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.event.ReturnCompletedEvent;
import com.yeni.backoffice.core.commerce.service.CommerceReturnService;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.PaymentCancelRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 반품(Return) 도메인과 결제(Payment/정산) 도메인을 잇는 유일한 연결점.
 * Return 도메인은 이 클래스의 존재도, PaymentCancelService의 존재도 모른 채 {@link ReturnCompletedEvent}만
 * 발행한다 — 실제 환불(PG 취소)은 여기서 기존 {@link PaymentCancelService#cancelPayment}를 그대로 재사용해서
 * 처리한다. 이렇게 하면:
 *   1) PG 승인취소/매입후취소 구분, 취소 매출(SaleType.CANCEL) 원장 생성, 그 원장이 다음 정산 배치 집계에
 *      자동으로 포함되는 것까지 — 이미 검증된 결제 취소 파이프라인을 그대로 재사용해서 새로 만들지 않는다.
 *      (정산 확정 전 취소면 원 매출과 상계되고, 정산 확정 후 취소면 이 취소 매출이 다음 배치의 차감 항목이
 *      되므로 "정산 시점에 따라 처리가 갈리는" 실무 흐름이 별도 코드 없이 자연히 재현된다.)
 *   2) AFTER_COMMIT에서만 실행되므로, 반품 완료 트랜잭션(재고 복원 등)이 실제로 커밋된 뒤에만 PG 취소를
 *      호출한다 — 반품 처리가 롤백됐는데 환불만 나가는 상황을 막는다.
 *   3) 커밋 이후 실행이라 여기서 예외가 나도 원래 요청(반품 완료 처리 자체)을 실패로 되돌릴 수 없으므로,
 *      반드시 예외를 삼키고 로그로 남긴다 — 실패를 감추는 게 아니라, 이미 끝난 트랜잭션을 부분 실패로
 *      오염시키지 않기 위해서다. 운영 환경이라면 이 로그를 알림/재처리 큐로 연결하면 된다.
 */
@Component
public class ReturnRefundEventListener {
    private static final Logger log = LoggerFactory.getLogger(ReturnRefundEventListener.class);

    private final PaymentCancelService paymentCancelService;
    private final CommerceReturnService returnService;

    public ReturnRefundEventListener(PaymentCancelService paymentCancelService, CommerceReturnService returnService) {
        this.paymentCancelService = paymentCancelService;
        this.returnService = returnService;
    }

    // AFTER_COMMIT은 원래 트랜잭션이 커밋된 뒤 같은 스레드에서 실행된다 — 이 시점에는 원래 트랜잭션의 리소스가
    // 정리되는 중이라, 여기서 새 DB 작업(특히 PaymentCancelService의 비관적 락 조회)을 시작하려면 반드시
    // REQUIRES_NEW로 완전히 독립된 새 트랜잭션을 열어야 한다 — 그렇지 않으면 "트랜잭션이 없다"는 오류가 난다.
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onReturnCompleted(ReturnCompletedEvent event) {
        try {
            paymentCancelService.cancelPayment(event.paymentId(),
                    new PaymentCancelRequest(event.refundAmount(), event.reason(), "RETURN-" + event.returnId()));
            // 이 호출은 위 cancelPayment와 같은(REQUIRES_NEW) 트랜잭션에 참여하므로 둘이 함께 커밋된다.
            returnService.markRefundSucceeded(event.returnId());
            log.info("반품 환불 처리 완료: returnId={}, orderNo={}, paymentId={}, refundAmount={}",
                    event.returnId(), event.orderNo(), event.paymentId(), event.refundAmount());
        } catch (Exception e) {
            log.error("반품 환불(PG 취소) 처리 실패 — 반품/재고 처리는 이미 커밋되었으니 수동 확인이 필요합니다. "
                            + "returnId={}, orderNo={}, paymentId={}, refundAmount={}",
                    event.returnId(), event.orderNo(), event.paymentId(), event.refundAmount(), e);
            // 위에서 실패했다면 이 메서드의 REQUIRES_NEW 트랜잭션은 이미 rollback-only로 마킹된 상태다 —
            // markRefundFailed 자체도 REQUIRES_NEW라 그 상태와 무관하게 독립적으로 커밋되어, 실패 사실은
            // 반드시 기록에 남는다.
            try {
                returnService.markRefundFailed(event.returnId(), e.getMessage());
            } catch (Exception recordingFailure) {
                log.error("반품 환불 실패 기록조차 남기지 못했습니다 — returnId={}", event.returnId(), recordingFailure);
            }
        }
    }
}
