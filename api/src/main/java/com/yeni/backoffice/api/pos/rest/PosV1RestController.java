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
import com.yeni.backoffice.core.pos.service.PosCatalogSyncService;
import com.yeni.backoffice.core.pos.service.PosTerminalAuthenticationService;
import com.yeni.backoffice.core.pos.service.PosSyncManagementService;
import com.yeni.backoffice.core.pos.service.PosOrderService;
import com.yeni.backoffice.core.pos.service.PosClientLogService;
import com.yeni.backoffice.core.pos.service.PosTerminalSettingsService;
import com.yeni.backoffice.core.pos.service.PosPaymentService;
import com.yeni.backoffice.core.pos.service.PosInventoryLookupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

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
    private final PosCatalogSyncService catalogSyncService;
    private final PosSyncManagementService syncManagementService;
    private final PosOrderService posOrderService;
    private final PosClientLogService clientLogService;
    private final PosTerminalSettingsService terminalSettingsService;
    private final PosPaymentService posPaymentService;
    private final PosInventoryLookupService inventoryLookupService;
    private final ObjectMapper objectMapper;

    public PosV1RestController(PosTerminalAuthenticationService authenticationService,
            PosSaleCommandService saleService, PosCatalogSyncService catalogSyncService,
            PosSyncManagementService syncManagementService, PosOrderService posOrderService,
            PosClientLogService clientLogService,PosTerminalSettingsService terminalSettingsService,
            PosPaymentService posPaymentService,PosInventoryLookupService inventoryLookupService,ObjectMapper objectMapper) {
        this.authenticationService = authenticationService;
        this.saleService = saleService;
        this.catalogSyncService = catalogSyncService;
        this.syncManagementService = syncManagementService;
        this.posOrderService = posOrderService;
        this.clientLogService = clientLogService;
        this.terminalSettingsService = terminalSettingsService;
        this.posPaymentService = posPaymentService;
        this.inventoryLookupService = inventoryLookupService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/orders/{orderId}/payments")
    public ResponseEntity<PosPaymentService.PaymentResult> approvePayment(@PathVariable Long orderId,
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestHeader(REQUEST_ID_HEADER) String clientRequestId,@Valid @RequestBody PosPaymentService.PaymentCommand request){
        validateClientRequestId(clientRequestId);PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(posPaymentService.approve(terminal,orderId,clientRequestId.trim(),requestHash(request),request));
    }

    @GetMapping("/inventory")
    public ResponseEntity<List<PosInventoryLookupService.InventoryItem>> inventory(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestParam(required=false) String keyword,@RequestParam(required=false) Integer limit){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(inventoryLookupService.search(terminal,keyword,limit));
    }

    @GetMapping("/settings")
    public ResponseEntity<PosTerminalSettingsService.TerminalSettings> settings(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(terminalSettingsService.settings(terminal));
    }

    @GetMapping("/diagnostics/connection")
    public ResponseEntity<PosTerminalSettingsService.ConnectionDiagnostic> connectionDiagnostic(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(terminalSettingsService.diagnostic(terminal));
    }

    @PostMapping("/logs")
    public ResponseEntity<PosClientLogService.LogBatchResult> receiveLogs(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestBody List<PosClientLogService.LogCommand> logs){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(clientLogService.receive(terminal,logs));
    }

    @GetMapping("/logs")
    public ResponseEntity<List<PosClientLogService.LogView>> logs(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestParam(required=false) Integer limit){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(clientLogService.list(terminal,limit));
    }

    @PostMapping("/logs/{logId}/retried")
    public ResponseEntity<PosClientLogService.LogView> markLogRetried(
            @PathVariable Long logId,@RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,@RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(clientLogService.markRetried(terminal,logId));
    }

    @GetMapping("/orders")
    public ResponseEntity<com.yeni.backoffice.core.commerce.service.CommerceOrderQueryService.OrderPage> orders(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,@RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestParam(required=false) Integer page,@RequestParam(required=false) Integer size){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(posOrderService.list(terminal,page,size));
    }

    @GetMapping("/orders/{orderId}")
    public ResponseEntity<com.yeni.backoffice.core.commerce.dto.CommerceOrderDtos.CommerceOrderResponse> order(
            @PathVariable Long orderId,@RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,@RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(posOrderService.detail(terminal,orderId));
    }

    @PostMapping("/orders/{orderId}/cancel")
    public ResponseEntity<PosOrderService.CancelResult> cancelOrder(
            @PathVariable Long orderId,@RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,@RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestHeader(REQUEST_ID_HEADER) String clientRequestId,
            @Valid @RequestBody PosV1Dtos.CancelRequest request){
        validateClientRequestId(clientRequestId);
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(posOrderService.cancel(terminal,orderId,clientRequestId.trim(),requestHash(request),
                request.cancelAmount(),request.reason()));
    }

    @PostMapping("/sync/executions")
    public ResponseEntity<PosSyncManagementService.SyncExecution> reportSync(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion,
            @RequestBody PosSyncManagementService.SyncReport report){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(syncManagementService.report(terminal,report));
    }

    @GetMapping("/sync/status")
    public ResponseEntity<PosSyncManagementService.SyncOverview> syncStatus(
            @RequestHeader(STORE_HEADER) Long storeId,@RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(syncManagementService.overview(terminal));
    }

    @PostMapping("/sync/executions/{executionId}/retry")
    public ResponseEntity<PosSyncManagementService.SyncExecution> retrySync(
            @PathVariable Long executionId,@RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,@RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(syncManagementService.requestRetry(terminal,executionId));
    }

    @PostMapping("/sync/executions/{executionId}/resolve")
    public ResponseEntity<PosSyncManagementService.SyncExecution> resolveSync(
            @PathVariable Long executionId,@RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,@RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value=VERSION_HEADER,required=false) String appVersion){
        PosTerminal terminal=authenticationService.authenticate(storeId,terminalCode,credential,appVersion);
        return ResponseEntity.ok(syncManagementService.resolve(terminal,executionId));
    }

    @GetMapping("/sync/catalog")
    public ResponseEntity<PosCatalogSyncService.CatalogPage> syncCatalog(
            @RequestHeader(STORE_HEADER) Long storeId,
            @RequestHeader(TERMINAL_HEADER) String terminalCode,
            @RequestHeader(CREDENTIAL_HEADER) String credential,
            @RequestHeader(value = VERSION_HEADER, required = false) String appVersion,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer limit) {
        PosTerminal terminal = authenticationService.authenticate(storeId, terminalCode, credential, appVersion);
        return ResponseEntity.ok(catalogSyncService.pull(terminal.getStoreId(), cursor, limit));
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
        validateClientRequestId(clientRequestId);
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

    private void validateClientRequestId(String clientRequestId){
        if(!StringUtils.hasText(clientRequestId)||clientRequestId.trim().length()>80)
            throw new BusinessException(ErrorCode.INVALID_REQUEST,"X-Client-Request-Id는 1~80자여야 합니다.");
    }

    private String requestHash(Object request) {
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
