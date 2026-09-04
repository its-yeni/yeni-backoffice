package com.yeni.backoffice.core.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 분개선 1줄. 한 줄은 차변 또는 대변 중 한쪽에만 금액이 있고 반대쪽은 0이다.
 * {@code accountName}은 조회 편의를 위한 비정규화 값이다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_line", indexes = {
        @Index(name = "idx_journal_line_entry", columnList = "journalEntryId"),
        @Index(name = "idx_journal_line_account", columnList = "accountCode"),
        @Index(name = "idx_journal_line_store_account", columnList = "storeId,accountCode")
})
public class JournalLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long journalEntryId;

    /** 전표의 storeId를 비정규화해 계정×매장 집계를 조인 없이 할 수 있게 한다. */
    private Long storeId;

    @Column(nullable = false)
    private int lineNo;

    @Column(nullable = false, length = 10)
    private String accountCode;

    @Column(nullable = false, length = 60)
    private String accountName;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal credit;
}
