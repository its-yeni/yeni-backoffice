package com.yeni.backoffice.api.pos.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yeni.backoffice.api.pos.rest.PosV1Dtos.ConnectResponse;
import com.yeni.backoffice.api.pos.rest.PosV1Dtos.SaleCreateRequest;
import com.yeni.backoffice.api.pos.rest.PosV1Dtos.SaleCreateResponse;
import com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderCreateRequest;
import com.yeni.backoffice.core.common.exception.BusinessException;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import com.yeni.backoffice.core.pos.service.PosSaleCommandService;
import com.yeni.backoffice.core.pos.service.PosTerminalAuthenticationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@RestController
@RequestMapping("/api/pos/v1")
public class PosV1RestController {
    static final String STORE_HEADER = "X-Store-Id";
    static final String TERMINAL_HEADER = "X-POS-Terminal";
    static final String CREDENTIAL_HEADER = "X-POS-Credential";
    static final String VERSION_HEADER = "X-POS-App-Version";
    static final String REQUEST_ID_HEADER = "X-Client-Request-Id";

    private final PosTerminalAuthenticationService authenticationService;
    private final PosSaleCommandService saleService;
    private final ObjectMapper objectMapper;

    public PosV1RestController(PosTerminalAuthenticationService authenticationService,
            PosSaleCommandService saleService, ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.saleService = saleService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/session/connect")
    public ResponseEntity<ConnectResponse> connect(
            @RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value = VERSION_HEADER, required = false) String appVersion) {
        PosTerminal terminal = authenticationService.authenticate(storeId, terminalCode, credential, appVersion);
        return ResponseEntity.ok(new ConnectResponse(terminal.getId(), terminal.getStoreId(),
                terminal.getTerminalCode(), terminal.getTerminalName(), terminal.getAppVersion(),
                terminal.getLastConnectedAt(), "CONNECTED"));
    }

    @PostMapping("/sales")
    public ResponseEntity<SaleCreateResponse> createSale(
            @RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value = VERSION_HEADER, required = false) String appVersion,
            @RequestHeader(REQUEST_ID_HEADER) String clientRequestId,
            @Valid @RequestBody SaleCreateRequest request) {
        if (!StringUtils.hasText(clientRequestId) || clientRequestId.trim().length() > 80) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "X-Client-Request-Id는 1~80자여야 합니다.");
        }
        PosTerminal terminal = authenticationService.authenticate(storeId, terminalCode, credential, appVersion);
        CommerceOrderCreateRequest orderRequest = new CommerceOrderCreateRequest(
                orderNumber(terminal, request), buyerName(request), request.buyerPhone(), request.items(), null);
        PosSaleCommandService.SaleResult result = saleService.create(terminal, clientRequestId.trim(),
                requestHash(request), orderRequest);
        return ResponseEntity.ok(new SaleCreateResponse(clientRequestId.trim(), result.replay(),
                result.requestStatus(), result.order()));
    }

    private String buyerName(SaleCreateRequest request) {
        return StringUtils.hasText(request.buyerName()) ? request.buyerName().trim() : "POS 고객";
    }

    private String orderNumber(PosTerminal terminal, SaleCreateRequest request) {
        if (StringUtils.hasText(request.localOrderNo())) {
            return "POS-" + terminal.getTerminalCode() + "-" + request.localOrderNo().trim();
        }
        return null;
    }

    private String requestHash(SaleCreateRequest request) {
        try {
            return sha256(objectMapper.writeValueAsBytes(request));
        } catch (JsonProcessingException serializationFailure) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "POS 주문 요청을 해석할 수 없습니다.");
        }
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 algorithm is not available.", impossible);
        }
    }
}
