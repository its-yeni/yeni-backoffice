package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.commerce.repository.CommerceStoreRepository;
import com.yeni.backoffice.core.common.exception.ErrorCode;
import com.yeni.backoffice.core.common.exception.NotFoundException;
import com.yeni.backoffice.core.payment.dto.GlDtos.GeneralLedgerResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.IncomeStatementResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.IncomeStatementRow;
import com.yeni.backoffice.core.payment.dto.GlDtos.JournalEntryPageResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.JournalEntryResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.LedgerLine;
import com.yeni.backoffice.core.payment.dto.GlDtos.StoreIncomeRow;
import com.yeni.backoffice.core.payment.dto.GlDtos.StoreIncomeStatementResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.TrialBalanceResponse;
import com.yeni.backoffice.core.payment.dto.GlDtos.TrialBalanceRow;
import com.yeni.backoffice.core.payment.entity.JournalEntry;
import com.yeni.backoffice.core.payment.entity.JournalLine;
import com.yeni.backoffice.core.payment.enums.AccountType;
import com.yeni.backoffice.core.payment.repository.JournalEntryRepository;
import com.yeni.backoffice.core.payment.repository.JournalLineRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** 분개 데이터를 시산표 / 총계정원장 / 손익계산서(전사·매장별)로 집계한다. 순수 읽기 전용. */
@Service
public class GlReportService {

    private static final String NO_STORE = "(매장 미지정)";

    private final JournalEntryRepository entryRepository;
    private final JournalLineRepository lineRepository;
    private final AccountNameResolver accounts;
    private final CommerceStoreRepository storeRepository;

    public GlReportService(
            JournalEntryRepository entryRepository,
            JournalLineRepository lineRepository,
            AccountNameResolver accounts,
            CommerceStoreRepository storeRepository) {
        this.entryRepository = entryRepository;
        this.lineRepository = lineRepository;
        this.accounts = accounts;
        this.storeRepository = storeRepository;
    }

    @Transactional(readOnly = true)
    public JournalEntryPageResponse journalEntries(LocalDate from, LocalDate to, Long storeId, int page, int size) {
        LocalDate start = from == null ? LocalDate.now().minusMonths(3) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        Map<Long, String> storeNames = storeNames();

        List<JournalEntry> all = entryRepository.findByEntryDateBetweenOrderByEntryDateDescIdDesc(
                        start, end, PageRequest.of(0, 5000)).getContent().stream()
                .filter(e -> storeId == null || Objects.equals(e.getStoreId(), storeId))
                .toList();
        int fromIndex = Math.min(safePage * safeSize, all.size());
        int toIndex = Math.min(fromIndex + safeSize, all.size());
        List<JournalEntry> pageItems = all.subList(fromIndex, toIndex);
        Map<Long, List<JournalLine>> linesByEntry = groupLines(pageItems.stream().map(JournalEntry::getId).toList());

        List<JournalEntryResponse> data = pageItems.stream()
                .map(entry -> JournalEntryResponse.from(entry,
                        linesByEntry.getOrDefault(entry.getId(), List.of()),
                        storeLabel(entry.getStoreId(), storeNames)))
                .toList();
        return new JournalEntryPageResponse(data, all.size(), safePage, safeSize);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(LocalDate asOf, Long storeId) {
        LocalDate cutoff = asOf == null ? LocalDate.now() : asOf;
        List<JournalLine> lines = linesUpTo(cutoff, storeId);

        Map<String, BigDecimal[]> byAccount = new LinkedHashMap<>(); // code -> [debit, credit]
        lines.forEach(line -> {
            BigDecimal[] acc = byAccount.computeIfAbsent(line.getAccountCode(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            acc[0] = acc[0].add(line.getDebit());
            acc[1] = acc[1].add(line.getCredit());
        });

        Map<String, GlAccounts.AccountDef> chart = accounts.allByCode();
        List<TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (Map.Entry<String, BigDecimal[]> e : byAccount.entrySet()) {
            String code = e.getKey();
            BigDecimal debit = e.getValue()[0];
            BigDecimal credit = e.getValue()[1];
            BigDecimal net = debit.subtract(credit);
            GlAccounts.AccountDef def = chart.get(code);
            rows.add(new TrialBalanceRow(
                    code,
                    def != null ? def.name() : accounts.name(code),
                    (def != null ? def.type() : accounts.type(code)).name(),
                    debit, credit,
                    net.signum() > 0 ? net : BigDecimal.ZERO,
                    net.signum() < 0 ? net.negate() : BigDecimal.ZERO));
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
        }
        rows.sort(Comparator.comparing(TrialBalanceRow::accountCode));
        return new TrialBalanceResponse(cutoff, storeId, storeLabelOrNull(storeId), rows,
                totalDebit, totalCredit, totalDebit.compareTo(totalCredit) == 0);
    }

    @Transactional(readOnly = true)
    public GeneralLedgerResponse generalLedger(String accountCode, LocalDate from, LocalDate to, Long storeId) {
        LocalDate start = from == null ? LocalDate.now().minusMonths(3) : from;
        LocalDate end = to == null ? LocalDate.now() : to;

        List<JournalLine> accountLines = lineRepository.findByAccountCodeOrderByJournalEntryIdAscLineNoAsc(accountCode).stream()
                .filter(l -> storeId == null || Objects.equals(l.getStoreId(), storeId))
                .toList();
        if (accountLines.isEmpty() && accounts.allByCode().get(accountCode) == null) {
            throw new NotFoundException(ErrorCode.NOT_FOUND, "존재하지 않는 계정과목입니다: " + accountCode);
        }
        Map<Long, JournalEntry> entryById = new LinkedHashMap<>();
        entryRepository.findByIdIn(new java.util.HashSet<>(accountLines.stream().map(JournalLine::getJournalEntryId).toList()))
                .forEach(entry -> entryById.put(entry.getId(), entry));

        AccountType type = accounts.type(accountCode);
        boolean debitNormal = type.isDebitNormal();

        List<JournalLine> ordered = new ArrayList<>(accountLines);
        ordered.sort(Comparator.comparing((JournalLine l) -> {
            JournalEntry entry = entryById.get(l.getJournalEntryId());
            return entry != null ? entry.getEntryDate() : LocalDate.MIN;
        }).thenComparing(JournalLine::getJournalEntryId).thenComparing(JournalLine::getLineNo));

        BigDecimal opening = BigDecimal.ZERO;
        BigDecimal running = BigDecimal.ZERO;
        BigDecimal debitTotal = BigDecimal.ZERO;
        BigDecimal creditTotal = BigDecimal.ZERO;
        List<LedgerLine> out = new ArrayList<>();
        for (JournalLine l : ordered) {
            JournalEntry entry = entryById.get(l.getJournalEntryId());
            LocalDate date = entry != null ? entry.getEntryDate() : LocalDate.MIN;
            BigDecimal delta = debitNormal ? l.getDebit().subtract(l.getCredit()) : l.getCredit().subtract(l.getDebit());
            if (date.isBefore(start)) {
                opening = opening.add(delta);
                running = opening;
                continue;
            }
            if (date.isAfter(end)) {
                continue;
            }
            running = running.add(delta);
            debitTotal = debitTotal.add(l.getDebit());
            creditTotal = creditTotal.add(l.getCredit());
            out.add(new LedgerLine(
                    l.getJournalEntryId(), date,
                    entry != null ? entry.getDescription() : "",
                    entry != null ? entry.getSourceRef() : null,
                    l.getDebit(), l.getCredit(), running));
        }
        return new GeneralLedgerResponse(
                accountCode, accounts.name(accountCode), type.name(),
                start, end, opening, running, debitTotal, creditTotal, out);
    }

    @Transactional(readOnly = true)
    public IncomeStatementResponse incomeStatement(LocalDate from, LocalDate to, Long storeId) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<JournalLine> lines = linesInPeriod(start, end, storeId);

        Map<String, BigDecimal> revenueByAccount = new LinkedHashMap<>();
        Map<String, BigDecimal> expenseByAccount = new LinkedHashMap<>();
        for (JournalLine line : lines) {
            AccountType type = accounts.type(line.getAccountCode());
            if (type == AccountType.REVENUE) {
                revenueByAccount.merge(line.getAccountCode(), line.getCredit().subtract(line.getDebit()), BigDecimal::add);
            } else if (type == AccountType.EXPENSE) {
                expenseByAccount.merge(line.getAccountCode(), line.getDebit().subtract(line.getCredit()), BigDecimal::add);
            }
        }

        List<IncomeStatementRow> revenues = toRows(revenueByAccount);
        List<IncomeStatementRow> expenses = toRows(expenseByAccount);
        BigDecimal totalRevenue = sum(revenues);
        BigDecimal totalExpense = sum(expenses);
        return new IncomeStatementResponse(start, end, storeId, storeLabelOrNull(storeId),
                revenues, totalRevenue, expenses, totalExpense, totalRevenue.subtract(totalExpense));
    }

    /** 매장별 손익: 기간 내 모든 전표를 매장 차원으로 쪼개 매장마다 수익·비용·순이익을 낸다. */
    @Transactional(readOnly = true)
    public StoreIncomeStatementResponse incomeStatementByStore(LocalDate from, LocalDate to) {
        LocalDate start = from == null ? LocalDate.now().withDayOfMonth(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        List<JournalLine> lines = linesInPeriod(start, end, null);
        Map<Long, String> storeNames = storeNames();

        Map<Long, BigDecimal[]> byStore = new LinkedHashMap<>(); // storeId(-1 = null) -> [revenue, expense]
        for (JournalLine line : lines) {
            AccountType type = accounts.type(line.getAccountCode());
            if (type != AccountType.REVENUE && type != AccountType.EXPENSE) {
                continue;
            }
            Long key = line.getStoreId();
            BigDecimal[] acc = byStore.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if (type == AccountType.REVENUE) {
                acc[0] = acc[0].add(line.getCredit().subtract(line.getDebit()));
            } else {
                acc[1] = acc[1].add(line.getDebit().subtract(line.getCredit()));
            }
        }

        List<StoreIncomeRow> stores = new ArrayList<>();
        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;
        for (Map.Entry<Long, BigDecimal[]> e : byStore.entrySet()) {
            BigDecimal revenue = e.getValue()[0];
            BigDecimal expense = e.getValue()[1];
            stores.add(new StoreIncomeRow(e.getKey(), storeLabel(e.getKey(), storeNames),
                    revenue, expense, revenue.subtract(expense)));
            totalRevenue = totalRevenue.add(revenue);
            totalExpense = totalExpense.add(expense);
        }
        stores.sort(Comparator.comparing(StoreIncomeRow::netIncome).reversed());
        return new StoreIncomeStatementResponse(start, end, stores,
                totalRevenue, totalExpense, totalRevenue.subtract(totalExpense));
    }

    // ── 헬퍼 ──────────────────────────────────────────────

    private List<JournalLine> linesUpTo(LocalDate cutoff, Long storeId) {
        List<JournalEntry> entries = entryRepository.findByEntryDateLessThanEqual(cutoff).stream()
                .filter(e -> storeId == null || Objects.equals(e.getStoreId(), storeId))
                .toList();
        return groupLines(entries.stream().map(JournalEntry::getId).toList()).values().stream()
                .flatMap(List::stream).toList();
    }

    private List<JournalLine> linesInPeriod(LocalDate start, LocalDate end, Long storeId) {
        List<JournalEntry> entries = entryRepository.findByEntryDateLessThanEqual(end).stream()
                .filter(e -> !e.getEntryDate().isBefore(start))
                .filter(e -> storeId == null || Objects.equals(e.getStoreId(), storeId))
                .toList();
        return groupLines(entries.stream().map(JournalEntry::getId).toList()).values().stream()
                .flatMap(List::stream).toList();
    }

    private List<IncomeStatementRow> toRows(Map<String, BigDecimal> byAccount) {
        return byAccount.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> new IncomeStatementRow(e.getKey(), accounts.name(e.getKey()), e.getValue()))
                .toList();
    }

    private BigDecimal sum(List<IncomeStatementRow> rows) {
        return rows.stream().map(IncomeStatementRow::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Map<Long, List<JournalLine>> groupLines(List<Long> entryIds) {
        Map<Long, List<JournalLine>> grouped = new LinkedHashMap<>();
        if (entryIds.isEmpty()) {
            return grouped;
        }
        lineRepository.findByJournalEntryIdInOrderByJournalEntryIdAscLineNoAsc(entryIds)
                .forEach(line -> grouped.computeIfAbsent(line.getJournalEntryId(), k -> new ArrayList<>()).add(line));
        return grouped;
    }

    private Map<Long, String> storeNames() {
        Map<Long, String> map = new LinkedHashMap<>();
        storeRepository.findAll().forEach(store -> map.put(store.getId(), store.getStoreName()));
        return map;
    }

    private String storeLabel(Long storeId, Map<Long, String> names) {
        if (storeId == null) {
            return NO_STORE;
        }
        return names.getOrDefault(storeId, "매장 #" + storeId);
    }

    private String storeLabelOrNull(Long storeId) {
        return storeId == null ? null : storeLabel(storeId, storeNames());
    }
}
