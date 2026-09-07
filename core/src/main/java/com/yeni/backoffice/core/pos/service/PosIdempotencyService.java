package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.pos.entity.PosInboundRequest;
import com.yeni.backoffice.core.pos.enums.PosRequestType;
import com.yeni.backoffice.core.pos.repository.PosInboundRequestRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class PosIdempotencyService {
    private static final int DEFAULT_MAX_RETRY_COUNT = 5;

    private final PosInboundRequestRepository requests;
    private final PosRequestRegistrationService registrationService;

    public PosIdempotencyService(
            PosInboundRequestRepository requests,
            PosRequestRegistrationService registrationService) {
        this.requests = requests;
        this.registrationService = registrationService;
    }

    public BeginResult begin(
            Long terminalId,
            Long storeId,
            String clientRequestId,
            PosRequestType requestType,
            String requestHash) {
        validateKey(terminalId, storeId, clientRequestId, requestType, requestHash);
        PosInboundRequest existing = requests
                .findByTerminalIdAndClientRequestId(terminalId, clientRequestId)
                .orElse(null);
        if (existing != null) return replay(existing, requestType, requestHash);

        try {
            return new BeginResult(registrationService.register(
                    terminalId, storeId, clientRequestId.trim(), requestType, requestHash), false);
        } catch (DataIntegrityViolationException concurrentRequest) {
            PosInboundRequest concurrent = requests
                    .findByTerminalIdAndClientRequestId(terminalId, clientRequestId.trim())
                    .orElseThrow(() -> concurrentRequest);
            return replay(concurrent, requestType, requestHash);
        }
    }

    @Transactional
    public void markProcessing(Long requestId) {
        requireRequest(requestId).markProcessing();
    }

    @Transactional
    public void markSucceeded(Long requestId, String serverReference) {
        requireRequest(requestId).markSucceeded(serverReference, LocalDateTime.now());
    }

    @Transactional
    public void markFailed(Long requestId, String errorCode, String errorMessage) {
        requireRequest(requestId).markFailed(errorCode, errorMessage, DEFAULT_MAX_RETRY_COUNT);
    }

    private BeginResult replay(PosInboundRequest existing, PosRequestType requestType, String requestHash) {
        if (!existing.getRequestType().equals(requestType) || !existing.getRequestHash().equals(requestHash)) {
            throw new ConflictException(ErrorCode.CONFLICT,
                    "같은 clientRequestId가 다른 요청 내용으로 이미 사용되었습니다.");
        }
        return new BeginResult(existing, true);
    }

    private PosInboundRequest requireRequest(Long requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.NOT_FOUND, "POS 요청 이력을 찾을 수 없습니다."));
    }

    private void validateKey(Long terminalId, Long storeId, String clientRequestId,
            PosRequestType requestType, String requestHash) {
        if (terminalId == null || storeId == null || requestType == null
                || !StringUtils.hasText(clientRequestId) || !StringUtils.hasText(requestHash)) {
            throw new ConflictException(ErrorCode.INVALID_REQUEST, "POS 멱등성 요청 키가 올바르지 않습니다.");
        }
    }

    public record BeginResult(PosInboundRequest request, boolean replay) {}
}
