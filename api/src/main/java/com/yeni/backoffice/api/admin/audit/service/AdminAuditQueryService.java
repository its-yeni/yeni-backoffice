package com.yeni.backoffice.api.admin.audit.service;

import com.yeni.backoffice.api.admin.audit.dto.AdminAuditLogResponse;
import com.yeni.backoffice.core.commerce.entity.InventoryTransaction;
import com.yeni.backoffice.core.commerce.repository.InventoryTransactionRepository;
import com.yeni.backoffice.core.payment.entity.AuditLog;
import com.yeni.backoffice.core.payment.entity.SettlementLog;
import com.yeni.backoffice.core.payment.repository.AuditLogRepository;
import com.yeni.backoffice.core.payment.repository.SettlementLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class AdminAuditQueryService {
    private final AuditLogRepository audits;
    private final InventoryTransactionRepository inventoryTransactions;
    private final SettlementLogRepository settlementLogs;

    public AdminAuditQueryService(AuditLogRepository audits,
                                  InventoryTransactionRepository inventoryTransactions,
                                  SettlementLogRepository settlementLogs) {
        this.audits = audits;
        this.inventoryTransactions = inventoryTransactions;
        this.settlementLogs = settlementLogs;
    }

    @Transactional(readOnly = true)
    public List<AdminAuditLogResponse> recent() {
        Stream<AdminAuditLogResponse> payment = audits.findTop500ByOrderByLoggedAtDesc().stream().map(this::fromAudit);
        Stream<AdminAuditLogResponse> inventory = inventoryTransactions.findTop200ByOrderByIdDesc().stream().map(this::fromInventory);
        Stream<AdminAuditLogResponse> settlement = settlementLogs.findAll().stream().map(this::fromSettlement);
        return Stream.concat(Stream.concat(payment, inventory), settlement)
                .sorted(Comparator.comparing(AdminAuditLogResponse::loggedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(500)
                .toList();
    }

    private AdminAuditLogResponse fromAudit(AuditLog log) {
        return new AdminAuditLogResponse("AUDIT", log.getId(), log.getDomainType(), log.getActionType(),
                log.getDomainType(), log.getReferenceKey(), value(log.getActor(), "SYSTEM"),
                value(log.getResultStatus(), "SUCCESS"), log.getDescription(), log.getBeforeValue(),
                log.getAfterValue(), log.getRequestId(), log.getLoggedAt());
    }

    private AdminAuditLogResponse fromInventory(InventoryTransaction row) {
        String before = "실재고 " + row.getStockBefore() + " · 예약 " + row.getReservedBefore();
        String after = "실재고 " + row.getStockAfter() + " · 예약 " + row.getReservedAfter();
        return new AdminAuditLogResponse("INVENTORY", row.getId(), "INVENTORY", row.getType().name(),
                row.getReferenceType(), row.getReferenceId() == null ? row.getSku() : String.valueOf(row.getReferenceId()),
                value(row.getActor(), "SYSTEM"), "SUCCESS", row.getReason(), before, after, null, row.getCreatedAt());
    }

    private AdminAuditLogResponse fromSettlement(SettlementLog log) {
        String actor = "BATCH_RUN".equals(log.getActionType()) ? "SYSTEM" : "ADMIN";
        return new AdminAuditLogResponse("SETTLEMENT", log.getId(), "SETTLEMENT", log.getActionType(),
                "SETTLEMENT", String.valueOf(log.getSettlementStatementId()), actor,
                log.getResultStatus(), log.getMessage(), null, null, null, log.getLoggedAt());
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
