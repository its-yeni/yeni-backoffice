package com.yeni.backoffice.core.pos.service;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderCreateRequest;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse;
import com.yeni.backoffice.core.commerce.service.CommerceOrderService;
import com.yeni.backoffice.core.common.exception.ConflictException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.pos.entity.PosInboundRequest;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import com.yeni.backoffice.core.pos.enums.PosRequestStatus;
import com.yeni.backoffice.core.pos.enums.PosRequestType;
import org.springframework.stereotype.Service;

@Service
public class PosSaleCommandService {
    private final PosIdempotencyService idempotencyService;
    private final CommerceOrderService orderService;

    public PosSaleCommandService(PosIdempotencyService idempotencyService, CommerceOrderService orderService) {
        this.idempotencyService = idempotencyService;
        this.orderService = orderService;
    }

    public SaleResult create(PosTerminal terminal, String clientRequestId, String requestHash,
            CommerceOrderCreateRequest orderRequest) {
        PosIdempotencyService.BeginResult begin = idempotencyService.begin(terminal.getId(), terminal.getStoreId(),
                clientRequestId, PosRequestType.SALE, requestHash);
        PosInboundRequest inbound = begin.request();
        if (begin.replay() && inbound.getStatus() == PosRequestStatus.SUCCEEDED) {
            return new SaleResult(orderService.getOrder(parseOrderId(inbound.getServerReference())), true,
                    inbound.getStatus().name());
        }
        if (begin.replay() && (inbound.getStatus() == PosRequestStatus.PROCESSING
                || inbound.getStatus() == PosRequestStatus.MANUAL_REVIEW)) {
            throw new ConflictException(ErrorCode.CONFLICT, "이미 처리 중이거나 수동 확인이 필요한 POS 요청입니다.");
        }

        idempotencyService.markProcessing(inbound.getId());
        try {
            CommerceOrderResponse order = orderService.createOrder(orderRequest, terminal.getStoreId());
            idempotencyService.markSucceeded(inbound.getId(), order.id().toString());
            return new SaleResult(order, false, PosRequestStatus.SUCCEEDED.name());
        } catch (RuntimeException failure) {
            idempotencyService.markFailed(inbound.getId(), errorCode(failure), safeMessage(failure));
            throw failure;
        }
    }

    private Long parseOrderId(String serverReference) {
        try {
            return Long.valueOf(serverReference);
        } catch (RuntimeException invalidReference) {
            throw new ConflictException(ErrorCode.CONFLICT, "완료된 POS 요청의 서버 참조값이 올바르지 않습니다.");
        }
    }

    private String errorCode(RuntimeException failure) {
        if (failure instanceof com.yeni.backoffice.core.common.exception.BusinessException business) {
            return business.getErrorCode().name();
        }
        return ErrorCode.INTERNAL_SERVER_ERROR.name();
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        if (message == null || message.isBlank()) return "POS 주문 처리에 실패했습니다.";
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    public record SaleResult(CommerceOrderResponse order, boolean replay, String requestStatus) {}
}
