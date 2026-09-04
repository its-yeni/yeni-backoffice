package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.dto.GlDtos.PostResult;
import com.yeni.backoffice.core.payment.entity.JournalEntry;
import com.yeni.backoffice.core.payment.entity.JournalLine;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.LedgerStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.repository.JournalEntryRepository;
import com.yeni.backoffice.core.payment.repository.JournalLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 매출 원장(SalesTransaction)과 정산 명세(SettlementStatement)를 원천으로 복식부기 분개를 생성한다.
 * 결제 처리 경로를 건드리지 않는 프로젝션이며, {@code sourceType + sourceId} 유니크로 멱등하다.
 *
 * <p>분개 규칙:
 * <ul>
 *   <li>SALE 원장 → (차) 미수금 / (대) 상품매출 + 부가세예수금</li>
 *   <li>CANCEL 원장 → (차) 매출환입 + 부가세예수금 / (대) 미수금</li>
 *   <li>정산 지급(PAID) → (차) 보통예금 + 지급수수료 + 부가세대급금 [+ 정산조정] / (대) 미수금</li>
 * </ul>
 */
@Service
public class GlPostingService {

    private static final Logger log = LoggerFactory.getLogger(GlPostingService.class);

    static final String SRC_SALES = "SALES_TRANSACTION";
    static final String SRC_SETTLEMENT = "SETTLEMENT_STATEMENT";

    private static final List<LedgerStatus> POSTABLE_LEDGER_STATUSES = List.of(LedgerStatus.POSTED, LedgerStatus.ADJUSTED);

    private final SalesTransactionRepository salesRepository;
    private final SettlementStatementRepository settlementRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final AccountNameResolver accountNames;

    public GlPostingService(
            SalesTransactionRepository salesRepository,
            SettlementStatementRepository settlementRepository,
            JournalEntryRepository journalEntryRepository,
            JournalLineRepository journalLineRepository,
            AccountNameResolver accountNames) {
        this.salesRepository = salesRepository;
        this.settlementRepository = settlementRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.accountNames = accountNames;
    }

    @Transactional
    public PostResult postPending() {
        int fromSales = 0;
        int fromSettlements = 0;
        int already = 0;
        int unbalanced = 0;

        for (SalesTransaction sale : salesRepository.findAll()) {
            if (sale.getLedgerStatus() == null || !POSTABLE_LEDGER_STATUSES.contains(sale.getLedgerStatus())) {
                continue;
            }
            if (journalEntryRepository.existsBySourceTypeAndSourceId(SRC_SALES, sale.getId())) {
                already++;
                continue;
            }
            JournalDraft draft = sale.getSaleType() == SaleType.CANCEL ? cancelDraft(sale) : saleDraft(sale);
            if (draft == null) {
                continue;
            }
            if (persist(draft)) {
                fromSales++;
            } else {
                unbalanced++;
            }
        }

        for (SettlementStatement statement : settlementRepository.findAll()) {
            if (statement.getSettlementStatus() != SettlementStatus.PAID) {
                continue;
            }
            if (journalEntryRepository.existsBySourceTypeAndSourceId(SRC_SETTLEMENT, statement.getId())) {
                already++;
                continue;
            }
            if (persist(settlementDraft(statement))) {
                fromSettlements++;
            } else {
                unbalanced++;
            }
        }

        return new PostResult(fromSales, fromSettlements, already, unbalanced);
    }

    private JournalDraft saleDraft(SalesTransaction sale) {
        BigDecimal supply = nz(sale.getSupplyAmount()).abs();
        BigDecimal vat = nz(sale.getVatAmount()).abs();
        BigDecimal total = nz(sale.getSaleAmount()).abs();
        if (total.signum() == 0) {
            return null;
        }
        JournalDraft draft = new JournalDraft(SRC_SALES, sale.getId(), sale.getStoreId(),
                entryDateOf(sale), "상품매출 인식 · " + sale.getOrderNo(), sale.getOrderNo());
        draft.debit(GlAccounts.RECEIVABLE, total);
        draft.credit(GlAccounts.SALES, supply);
        draft.credit(GlAccounts.VAT_OUTPUT, vat);
        return draft;
    }

    private JournalDraft cancelDraft(SalesTransaction cancel) {
        BigDecimal supply = nz(cancel.getSupplyAmount()).abs();
        BigDecimal vat = nz(cancel.getVatAmount()).abs();
        BigDecimal total = nz(cancel.getSaleAmount()).abs();
        if (total.signum() == 0) {
            return null;
        }
        JournalDraft draft = new JournalDraft(SRC_SALES, cancel.getId(), cancel.getStoreId(),
                entryDateOf(cancel), "매출 취소 · " + cancel.getOrderNo(), cancel.getOrderNo());
        draft.debit(GlAccounts.SALES_RETURN, supply);
        draft.debit(GlAccounts.VAT_OUTPUT, vat);
        draft.credit(GlAccounts.RECEIVABLE, total);
        return draft;
    }

    private JournalDraft settlementDraft(SettlementStatement s) {
        BigDecimal gross = nz(s.getGrossAmount());
        BigDecimal fee = nz(s.getFeeAmount());
        BigDecimal feeVat = nz(s.getVatAmount());
        BigDecimal net = nz(s.getNetAmount());
        // 유보·조정 등으로 생기는 차액은 정산조정 계정으로 흡수해 항상 대차를 맞춘다.
        BigDecimal plug = gross.subtract(net).subtract(fee).subtract(feeVat);

        JournalDraft draft = new JournalDraft(SRC_SETTLEMENT, s.getId(), s.getStoreId(),
                s.getSettlementDate(), "PG 정산 입금 · " + s.getMid() + " · " + s.getSettlementDate(),
                s.getMid());
        draft.debit(GlAccounts.BANK, net);
        draft.debit(GlAccounts.PG_FEE, fee);
        draft.debit(GlAccounts.VAT_INPUT, feeVat);
        if (plug.signum() > 0) {
            draft.debit(GlAccounts.SETTLEMENT_ADJ, plug);
        } else if (plug.signum() < 0) {
            draft.credit(GlAccounts.SETTLEMENT_ADJ, plug.negate());
        }
        draft.credit(GlAccounts.RECEIVABLE, gross);
        return draft;
    }

    /** @return 저장 성공 여부. 대차 불일치거나 유니크 충돌이면 false. */
    private boolean persist(JournalDraft draft) {
        if (draft == null) {
            return false;
        }
        if (draft.totalDebit().compareTo(draft.totalCredit()) != 0) {
            log.warn("분개 대차 불일치로 건너뜀: {}/{} 차변 {} 대변 {}",
                    draft.sourceType, draft.sourceId, draft.totalDebit(), draft.totalCredit());
            return false;
        }
        try {
            JournalEntry entry = journalEntryRepository.save(JournalEntry.builder()
                    .entryDate(draft.entryDate)
                    .storeId(draft.storeId)
                    .description(draft.description)
                    .sourceType(draft.sourceType)
                    .sourceId(draft.sourceId)
                    .sourceRef(draft.sourceRef)
                    .build());
            int lineNo = 1;
            List<JournalLine> lines = new ArrayList<>();
            for (JournalDraft.Line l : draft.lines) {
                lines.add(JournalLine.builder()
                        .journalEntryId(entry.getId())
                        .storeId(draft.storeId)
                        .lineNo(lineNo++)
                        .accountCode(l.code())
                        .accountName(accountNames.name(l.code()))
                        .debit(l.debit())
                        .credit(l.credit())
                        .build());
            }
            journalLineRepository.saveAll(lines);
            return true;
        } catch (DataIntegrityViolationException e) {
            // 동시 실행으로 같은 원천이 이미 분개됨 — 멱등하게 무시.
            log.debug("분개 유니크 충돌(이미 생성됨): {}/{}", draft.sourceType, draft.sourceId);
            return false;
        }
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static java.time.LocalDate entryDateOf(SalesTransaction sale) {
        if (sale.getBusinessDate() != null) {
            return sale.getBusinessDate();
        }
        if (sale.getOccurredAt() != null) {
            return sale.getOccurredAt().toLocalDate();
        }
        return sale.getCreatedAt() != null ? sale.getCreatedAt().toLocalDate() : java.time.LocalDate.now();
    }

    /** 저장 전 분개 초안. 같은 계정에 여러 번 debit/credit 하면 합산한다. */
    static final class JournalDraft {
        private final String sourceType;
        private final Long sourceId;
        private final Long storeId;
        private final java.time.LocalDate entryDate;
        private final String description;
        private final String sourceRef;
        private final List<Line> lines = new ArrayList<>();

        JournalDraft(String sourceType, Long sourceId, Long storeId,
                     java.time.LocalDate entryDate, String description, String sourceRef) {
            this.sourceType = sourceType;
            this.sourceId = sourceId;
            this.storeId = storeId;
            this.entryDate = entryDate;
            this.description = description;
            this.sourceRef = sourceRef;
        }

        void debit(String code, BigDecimal amount) {
            add(code, nz(amount).abs(), BigDecimal.ZERO);
        }

        void credit(String code, BigDecimal amount) {
            add(code, BigDecimal.ZERO, nz(amount).abs());
        }

        private void add(String code, BigDecimal debit, BigDecimal credit) {
            if (debit.signum() == 0 && credit.signum() == 0) {
                return;
            }
            for (int i = 0; i < lines.size(); i++) {
                Line existing = lines.get(i);
                if (existing.code().equals(code)) {
                    lines.set(i, new Line(code, existing.debit().add(debit), existing.credit().add(credit)));
                    return;
                }
            }
            lines.add(new Line(code, debit, credit));
        }

        BigDecimal totalDebit() {
            return lines.stream().map(Line::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        BigDecimal totalCredit() {
            return lines.stream().map(Line::credit).reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        record Line(String code, BigDecimal debit, BigDecimal credit) {
        }
    }
}
