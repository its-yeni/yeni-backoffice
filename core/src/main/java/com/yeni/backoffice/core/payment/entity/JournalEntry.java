package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 전표(분개 헤더). 하나의 회계 이벤트를 나타내며, 연결된 {@link JournalLine}들의 차변 합계 = 대변 합계가 항상 맞아야 한다.
 * {@code sourceType + sourceId} 유니크로 같은 원천(매출 원장/정산 명세)에 대한 중복 분개를 막는다.
 * 코드베이스 관례를 따라 분개선은 JPA 연관관계 대신 {@code journalEntryId} FK로 연결한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "journal_entry", uniqueConstraints = @UniqueConstraint(
        name = "uk_journal_entry_source", columnNames = {"sourceType", "sourceId"}))
public class JournalEntry extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDate entryDate;

    /** 분석 차원: 이 전표가 귀속되는 매장. 원천(매출 원장/정산 명세)의 매장을 그대로 상속한다. null이면 매장 미지정. */
    private Long storeId;

    @Column(nullable = false, length = 200)
    private String description;

    /** SALES_TRANSACTION | SETTLEMENT_STATEMENT */
    @Column(nullable = false, length = 30)
    private String sourceType;

    @Column(nullable = false)
    private Long sourceId;

    /** 화면 표시용 참조값 (주문번호, MID 등). */
    @Column(length = 120)
    private String sourceRef;
}
