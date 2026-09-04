package com.yeni.backoffice.core.payment.dto;

import com.yeni.backoffice.core.payment.entity.JournalEntry;
import com.yeni.backoffice.core.payment.entity.JournalLine;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class GlDtos {

    private GlDtos() {
    }

    public record PostResult(int createdFromSales, int createdFromSettlements, int alreadyPosted, int unbalancedSkipped) {
    }

    public record JournalLineResponse(String accountCode, String accountName, BigDecimal debit, BigDecimal credit) {
        public static JournalLineResponse from(JournalLine line) {
            return new JournalLineResponse(line.getAccountCode(), line.getAccountName(), line.getDebit(), line.getCredit());
        }
    }

    public record JournalEntryResponse(
            Long id,
            LocalDate entryDate,
            Long storeId,
            String storeName,
            String description,
            String sourceType,
            Long sourceId,
            String sourceRef,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            LocalDateTime createdAt,
            List<JournalLineResponse> lines
    ) {
        public static JournalEntryResponse from(JournalEntry entry, List<JournalLine> lines, String storeName) {
            BigDecimal debit = lines.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = lines.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            return new JournalEntryResponse(
                    entry.getId(), entry.getEntryDate(), entry.getStoreId(), storeName, entry.getDescription(),
                    entry.getSourceType(), entry.getSourceId(), entry.getSourceRef(),
                    debit, credit, debit.compareTo(credit) == 0, entry.getCreatedAt(),
                    lines.stream().map(JournalLineResponse::from).toList());
        }
    }

    public record JournalEntryPageResponse(List<JournalEntryResponse> data, long totalCount, int page, int size) {
    }

    public record TrialBalanceRow(
            String accountCode,
            String accountName,
            String accountType,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            BigDecimal debitBalance,
            BigDecimal creditBalance
    ) {
    }

    public record TrialBalanceResponse(
            LocalDate asOf,
            Long storeId,
            String storeName,
            List<TrialBalanceRow> rows,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced
    ) {
    }

    public record LedgerLine(
            Long journalEntryId,
            LocalDate entryDate,
            String description,
            String sourceRef,
            BigDecimal debit,
            BigDecimal credit,
            BigDecimal runningBalance
    ) {
    }

    public record GeneralLedgerResponse(
            String accountCode,
            String accountName,
            String accountType,
            LocalDate from,
            LocalDate to,
            BigDecimal openingBalance,
            BigDecimal closingBalance,
            BigDecimal debitTotal,
            BigDecimal creditTotal,
            List<LedgerLine> lines
    ) {
    }

    public record IncomeStatementRow(String accountCode, String accountName, BigDecimal amount) {
    }

    public record IncomeStatementResponse(
            LocalDate from,
            LocalDate to,
            Long storeId,
            String storeName,
            List<IncomeStatementRow> revenues,
            BigDecimal totalRevenue,
            List<IncomeStatementRow> expenses,
            BigDecimal totalExpense,
            BigDecimal netIncome
    ) {
    }

    /** 매장별 손익 한 줄. 매장 미지정 전표는 storeId=null, storeName="(매장 미지정)". */
    public record StoreIncomeRow(
            Long storeId,
            String storeName,
            BigDecimal revenue,
            BigDecimal expense,
            BigDecimal netIncome
    ) {
    }

    public record StoreIncomeStatementResponse(
            LocalDate from,
            LocalDate to,
            List<StoreIncomeRow> stores,
            BigDecimal totalRevenue,
            BigDecimal totalExpense,
            BigDecimal totalNetIncome
    ) {
    }
}
