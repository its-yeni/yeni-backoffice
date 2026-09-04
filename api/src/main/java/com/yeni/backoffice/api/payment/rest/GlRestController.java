package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.GlDtos.GeneralLedgerResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.IncomeStatementResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.JournalEntryPageResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.PostResult;
import com.yeni.backoffice.core.payment.dto.GlDtos.StoreIncomeStatementResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.TrialBalanceResponse;
import com.yeni.backoffice.core.payment.gl.GlPostingService;
import com.yeni.backoffice.core.payment.gl.GlReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/admin/api/gl")
@Tag(name = "General Ledger", description = "복식부기 분개장 · 시산표 · 손익계산서")
public class GlRestController {

    private final GlPostingService postingService;
    private final GlReportService reportService;

    public GlRestController(GlPostingService postingService, GlReportService reportService) {
        this.postingService = postingService;
        this.reportService = reportService;
    }

    @PostMapping("/post-pending")
    @Operation(summary = "미전기 분개 생성",
            description = "확정된 매출 원장과 지급 완료 정산 명세를 복식부기 분개로 전기합니다. 원천 기준으로 멱등합니다.")
    public ResponseEntity<PostResult> postPending() {
        return ResponseEntity.ok(postingService.postPending());
    }

    @GetMapping("/journal-entries")
    @Operation(summary = "전표(분개) 목록", description = "storeId로 매장을 지정하면 해당 매장 전표만 반환합니다.")
    public ResponseEntity<JournalEntryPageResponse> journalEntries(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reportService.journalEntries(from, to, storeId, page, size));
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "시산표", description = "계정별 차변·대변 합계와 잔액. 총 차변 = 총 대변이면 balanced=true. storeId로 매장 한정 가능.")
    public ResponseEntity<TrialBalanceResponse> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportService.trialBalance(asOf, storeId));
    }

    @GetMapping("/general-ledger/{accountCode}")
    @Operation(summary = "총계정원장", description = "특정 계정의 분개 내역과 잔액 추이. storeId로 매장 한정 가능.")
    public ResponseEntity<GeneralLedgerResponse> generalLedger(
            @PathVariable String accountCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportService.generalLedger(accountCode, from, to, storeId));
    }

    @GetMapping("/income-statement")
    @Operation(summary = "손익계산서", description = "기간 내 수익·비용 계정을 집계해 당기순이익을 계산합니다. storeId로 매장 한정 가능.")
    public ResponseEntity<IncomeStatementResponse> incomeStatement(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long storeId) {
        return ResponseEntity.ok(reportService.incomeStatement(from, to, storeId));
    }

    @GetMapping("/income-by-store")
    @Operation(summary = "매장별 손익", description = "기간 내 전표를 매장 차원으로 쪼개 매장마다 수익·비용·순이익을 냅니다.")
    public ResponseEntity<StoreIncomeStatementResponse> incomeByStore(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(reportService.incomeStatementByStore(from, to));
    }
}
