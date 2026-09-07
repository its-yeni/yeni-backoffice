package com.yeni.backoffice.api.pos.rest;

import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderItemCreateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public final class PosV1Dtos {
    private PosV1Dtos() {}

    public record ConnectResponse(Long terminalId, Long storeId, String terminalCode, String terminalName,
            String appVersion, LocalDateTime connectedAt, String serverStatus) {}

    public record SaleCreateRequest(String localOrderNo, String buyerName, String buyerPhone,
            @Valid @NotEmpty List<CommerceOrderItemCreateRequest> items) {}

    public record SaleCreateResponse(String clientRequestId, boolean replay, String requestStatus,
            com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse order) {}

    public record CancelRequest(@NotNull @Positive BigDecimal cancelAmount,String reason) {}
}
