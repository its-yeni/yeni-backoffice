package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.dto.GlDtos.PostResult;
import com.yeni.backoffice.core.payment.entity.JournalLine;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SettlementStatement;
import com.yeni.backoffice.core.payment.enums.LedgerStatus;
import com.yeni.backoffice.core.payment.enums.SaleStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SalesSettlementStatus;
import com.yeni.backoffice.core.payment.enums.SettlementStatus;
import com.yeni.backoffice.core.payment.repository.ChartOfAccountRepository;
import com.yeni.backoffice.core.payment.repository.JournalEntryRepository;
import com.yeni.backoffice.core.payment.repository.JournalLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import com.yeni.backoffice.core.payment.repository.SettlementStatementRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GlPostingServiceTest {

    private final SalesTransactionRepository salesRepository = mock(SalesTransactionRepository.class);
    private final SettlementStatementRepository settlementRepository = mock(SettlementStatementRepository.class);
    private final JournalEntryRepository journalEntryRepository = mock(JournalEntryRepository.class);
    private final JournalLineRepository journalLineRepository = mock(JournalLineRepository.class);
    private final ChartOfAccountRepository chartRepository = mock(ChartOfAccountRepository.class);

    private final GlPostingService service = new GlPostingService(
            salesRepository, settlementRepository, journalEntryRepository, journalLineRepository,
            new AccountNameResolver(chartRepository));

    {
        when(journalEntryRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(chartRepository.findByCode(any())).thenReturn(java.util.Optional.empty());
    }

    @Test
    void saleLedgerPostsBalancedEntryDebitingReceivableCreditingSalesAndVat() {
        when(salesRepository.findAll()).thenReturn(List.of(
                sale(1L, SaleType.SALE, new BigDecimal("25900"), new BigDecimal("23545"), new BigDecimal("2355"))));
        when(settlementRepository.findAll()).thenReturn(List.of());
        when(journalEntryRepository.existsBySourceTypeAndSourceId(any(), anyLong())).thenReturn(false);

        PostResult result = service.postPending();

        assertThat(result.createdFromSales()).isEqualTo(1);
        List<JournalLine> lines = captureSavedLines();
        assertThat(totalDebit(lines)).isEqualByComparingTo("25900");
        assertThat(totalCredit(lines)).isEqualByComparingTo("25900");
        assertThat(debitOf(lines, GlAccounts.RECEIVABLE)).isEqualByComparingTo("25900");
        assertThat(creditOf(lines, GlAccounts.SALES)).isEqualByComparingTo("23545");
        assertThat(creditOf(lines, GlAccounts.VAT_OUTPUT)).isEqualByComparingTo("2355");
        assertThat(lines).allMatch(l -> l.getStoreId() != null && l.getStoreId() == 7L);
    }

    @Test
    void cancelLedgerReversesTheSaleEntry() {
        when(salesRepository.findAll()).thenReturn(List.of(
                sale(2L, SaleType.CANCEL, new BigDecimal("-5000"), new BigDecimal("-4545"), new BigDecimal("-455"))));
        when(settlementRepository.findAll()).thenReturn(List.of());
        when(journalEntryRepository.existsBySourceTypeAndSourceId(any(), anyLong())).thenReturn(false);

        service.postPending();

        List<JournalLine> lines = captureSavedLines();
        assertThat(totalDebit(lines)).isEqualByComparingTo(totalCredit(lines));
        assertThat(debitOf(lines, GlAccounts.SALES_RETURN)).isEqualByComparingTo("4545");
        assertThat(debitOf(lines, GlAccounts.VAT_OUTPUT)).isEqualByComparingTo("455");
        assertThat(creditOf(lines, GlAccounts.RECEIVABLE)).isEqualByComparingTo("5000");
    }

    @Test
    void settlementPayoutBalancesBankFeeAndVatAgainstReceivable() {
        when(salesRepository.findAll()).thenReturn(List.of());
        when(settlementRepository.findAll()).thenReturn(List.of(
                settlement(9L, "25900", "700", "70", "25130")));
        when(journalEntryRepository.existsBySourceTypeAndSourceId(any(), anyLong())).thenReturn(false);

        PostResult result = service.postPending();

        assertThat(result.createdFromSettlements()).isEqualTo(1);
        List<JournalLine> lines = captureSavedLines();
        assertThat(totalDebit(lines)).isEqualByComparingTo("25900");
        assertThat(totalCredit(lines)).isEqualByComparingTo("25900");
        assertThat(debitOf(lines, GlAccounts.BANK)).isEqualByComparingTo("25130");
        assertThat(debitOf(lines, GlAccounts.PG_FEE)).isEqualByComparingTo("700");
        assertThat(debitOf(lines, GlAccounts.VAT_INPUT)).isEqualByComparingTo("70");
        assertThat(creditOf(lines, GlAccounts.RECEIVABLE)).isEqualByComparingTo("25900");
    }

    @Test
    void alreadyPostedSourceIsSkipped() {
        when(salesRepository.findAll()).thenReturn(List.of(
                sale(1L, SaleType.SALE, new BigDecimal("25900"), new BigDecimal("23545"), new BigDecimal("2355"))));
        when(settlementRepository.findAll()).thenReturn(List.of());
        when(journalEntryRepository.existsBySourceTypeAndSourceId(eq("SALES_TRANSACTION"), eq(1L))).thenReturn(true);

        PostResult result = service.postPending();

        assertThat(result.createdFromSales()).isZero();
        assertThat(result.alreadyPosted()).isEqualTo(1);
        verify(journalEntryRepository, never()).save(any());
    }

    private List<JournalLine> captureSavedLines() {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalLine>> captor = ArgumentCaptor.forClass(List.class);
        verify(journalLineRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private BigDecimal totalDebit(List<JournalLine> lines) {
        return lines.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCredit(List<JournalLine> lines) {
        return lines.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal debitOf(List<JournalLine> lines, String code) {
        return lines.stream().filter(l -> l.getAccountCode().equals(code))
                .map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal creditOf(List<JournalLine> lines, String code) {
        return lines.stream().filter(l -> l.getAccountCode().equals(code))
                .map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private SalesTransaction sale(long id, SaleType type, BigDecimal total, BigDecimal supply, BigDecimal vat) {
        return SalesTransaction.builder()
                .id(id).storeId(7L).sourceType(type.name()).sourceId(id).orderNo("ORD-" + id).tid("TID-" + id)
                .saleType(type).saleAmount(total).supplyAmount(supply).vatAmount(vat).totalAmount(total)
                .saleStatus(SaleStatus.APPROVED).ledgerStatus(LedgerStatus.POSTED)
                .settlementStatus(SalesSettlementStatus.NOT_SETTLED)
                .businessDate(LocalDate.now())
                .build();
    }

    private SettlementStatement settlement(long id, String gross, String fee, String feeVat, String net) {
        return SettlementStatement.builder()
                .id(id).settlementDate(LocalDate.now()).pgCompany("INICIS").mid("MID-1")
                .grossAmount(new BigDecimal(gross)).feeAmount(new BigDecimal(fee))
                .vatAmount(new BigDecimal(feeVat)).netAmount(new BigDecimal(net))
                .adjustmentAmount(BigDecimal.ZERO).holdAmount(BigDecimal.ZERO)
                .settlementStatus(SettlementStatus.PAID)
                .build();
    }
}
