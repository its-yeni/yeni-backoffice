package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementAdjustmentRequest;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementDetailPageResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementStatementResponse;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementPayRequest;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping({"/api/admin/settlements", "/admin/api/settlements"})
@Tag(name = "Settlement Operation", description = "일별 정산 배치, 정산 명세 조회, 확정, 지급 처리 API")
public class SettlementOperationRestController {

    private final SettlementOperationService settlementOperationService;

    public SettlementOperationRestController(SettlementOperationService settlementOperationService) {
        this.settlementOperationService = settlementOperationService;
    }

    @PostMapping("/batch/run")
    @Operation(summary = "일별 정산 초안 생성/재계산", description = "미정산 매출을 기준으로 정산 초안을 생성하고, 같은 날짜의 DRAFT 명세가 있으면 새 거래를 누적해 재계산합니다.")
    public ResponseEntity<SettlementStatementResponse> runBatch(@Valid @RequestBody(required = false) SettlementBatchRunRequest request) {
        return ResponseEntity.ok(settlementOperationService.runDailySettlement(request));
    }

    @GetMapping
    @Operation(summary = "정산 명세 목록 조회", description = "기간 기준 정산 명세 목록을 조회합니다.")
    public ResponseEntity<List<SettlementStatementResponse>> statements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(settlementOperationService.getStatements(startDate, endDate, storeId));
    }

    @GetMapping("/store-summary")
    @Operation(summary = "매장별 정산 요약", description = "기간 기준으로 매장이 받은 정산 총액·수수료·지급 완료액을 매장 단위로 집계합니다.")
    public ResponseEntity<List<com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.StoreSettlementSummary>> storeSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ResponseEntity.ok(settlementOperationService.getStoreSummary(startDate, endDate));
    }

    @GetMapping("/{statementId}")
    @Operation(summary = "정산 명세 상세 조회", description = "정산 명세와 포함된 매출 상세를 조회합니다.")
    public ResponseEntity<SettlementDetailPageResponse> statement(@PathVariable Long statementId) {
        return ResponseEntity.ok(settlementOperationService.getStatement(statementId));
    }

    @PostMapping("/{statementId}/confirm-request")
    @Operation(summary = "정산 확정 요청", description = "maker-checker 1단계 — DRAFT 명세를 확정 대기로 전환합니다.")
    public ResponseEntity<SettlementStatementResponse> confirmRequest(
            @PathVariable Long statementId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(settlementOperationService.requestConfirm(statementId, body == null ? null : body.get("actor")));
    }

    @PostMapping("/{statementId}/confirm")
    @Operation(summary = "정산 확정 승인", description = "maker-checker 2단계 — 확정 대기 명세를 CONFIRMED 로 승인합니다.")
    public ResponseEntity<SettlementStatementResponse> confirm(@PathVariable Long statementId) {
        return ResponseEntity.ok(settlementOperationService.confirmStatement(statementId));
    }

    @PostMapping("/{statementId}/payout-request")
    @Operation(summary = "정산 지급 요청", description = "maker-checker 1단계 — CONFIRMED 명세를 지급 대기로 전환합니다.")
    public ResponseEntity<SettlementStatementResponse> payoutRequest(
            @PathVariable Long statementId,
            @RequestBody(required = false) java.util.Map<String, String> body) {
        return ResponseEntity.ok(settlementOperationService.requestPayout(statementId, body == null ? null : body.get("actor")));
    }

    @PostMapping("/{statementId}/adjust")
    public ResponseEntity<SettlementStatementResponse> adjust(
            @PathVariable Long statementId,
            @RequestBody SettlementAdjustmentRequest request) {
        return ResponseEntity.ok(settlementOperationService.applyAdjustment(statementId, request));
    }

    @PostMapping("/{statementId}/pay")
    @Operation(summary = "정산 지급 처리", description = "CONFIRMED 상태의 정산 명세를 PAID 상태로 변경합니다.")
    public ResponseEntity<SettlementStatementResponse> pay(
            @PathVariable Long statementId,
            @RequestBody(required = false) SettlementPayRequest request) {
        return ResponseEntity.ok(settlementOperationService.markPaid(statementId, request));
    }
}
