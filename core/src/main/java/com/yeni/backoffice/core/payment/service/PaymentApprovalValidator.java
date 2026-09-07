package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.ValidationBusinessException;
import com.yeni.backoffice.core.payment.adapter.PaymentGatewayAdapter;
import com.yeni.backoffice.core.payment.dto.PaymentBridgeDtos.PaymentApproveRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisAuthResultRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.InicisReadyRequest;
import com.yeni.backoffice.core.payment.entity.PaymentAuthSession;
import com.yeni.backoffice.core.payment.repository.PaymentTransactionRepository;
import com.yeni.backoffice.core.payment.util.InicisSignatureService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Component
public class PaymentApprovalValidator {
    private final InicisSignatureService signatureService;
    private final PaymentTransactionRepository paymentRepository;

    public PaymentApprovalValidator(
            InicisSignatureService signatureService,
            PaymentTransactionRepository paymentRepository) {
        this.signatureService = signatureService;
        this.paymentRepository = paymentRepository;
    }

    public void validateApproveRequest(PaymentApproveRequest request) {
        if (request == null || !StringUtils.hasText(request.orderNo()) || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "주문번호와 0보다 큰 승인금액은 필수입니다.");
        }
    }

    public void validateReadyRequest(InicisReadyRequest request) {
        if (request == null || !StringUtils.hasText(request.orderNo()) || request.amount() == null
                || request.amount().compareTo(BigDecimal.ZERO) <= 0 || !StringUtils.hasText(request.buyerName())
                || !StringUtils.hasText(request.productName())) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "주문번호, 0보다 큰 금액, 구매자명, 상품명은 필수입니다.");
        }
    }

    public void validateAuthResultRequest(InicisAuthResultRequest request) {
        if (request == null || !StringUtils.hasText(request.mid()) || !StringUtils.hasText(request.orderNo())
                || !StringUtils.hasText(request.authToken()) || request.amount() == null
                || !StringUtils.hasText(request.resultCode()) || !StringUtils.hasText(request.signature())) {
            throw new ValidationBusinessException(ErrorCode.VALIDATION_ERROR, "인증 결과 요청값이 올바르지 않습니다.");
        }
    }

    public void validateAuthMatchesSession(InicisAuthResultRequest request, PaymentAuthSession session) {
        if (!session.getMid().equals(request.mid()) || !session.getOrderNo().equals(request.orderNo())
                || session.getAmount().compareTo(request.amount()) != 0) {
            throw new ValidationBusinessException(ErrorCode.INVALID_REQUEST, "인증 결과가 결제 세션과 일치하지 않습니다.");
        }
        if (!signatureService.matchesAuthSignature(request.orderNo(), request.amount(), request.authToken(), request.signature())) {
            throw new ValidationBusinessException(ErrorCode.INVALID_REQUEST, "인증 서명이 올바르지 않습니다.");
        }
    }

    public void validateApprovalResult(PaymentAuthSession session, PaymentGatewayAdapter.ApprovalResult result) {
        if (!StringUtils.hasText(result.tid()) || result.approvedAt() == null) {
            throw new ValidationBusinessException(ErrorCode.INVALID_REQUEST, "Mock 승인 응답값이 올바르지 않습니다.");
        }
        paymentRepository.findByTid(result.tid()).ifPresent(payment -> {
            throw new ConflictException(ErrorCode.PAYMENT_DUPLICATED_REQUEST, "이미 등록된 PG 거래번호입니다.");
        });
        paymentRepository.findByOrderNo(session.getOrderNo()).ifPresent(payment -> {
            throw new ConflictException(ErrorCode.PAYMENT_ALREADY_APPROVED);
        });
    }
}
