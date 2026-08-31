package com.yeni.backoffice.core.payment.entity;

import com.yeni.backoffice.core.common.entity.BaseTimeEntity;
import com.yeni.backoffice.core.payment.enums.SaleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 매출 명세 라인 — {@link SalesTransaction}(매출 헤더, 결제/취소 단위)의 상품 단위 분해.
 * 헤더는 정산·중복방어·상태전이를 그대로 담당하고, 이 라인은 "무슨 상품이 얼마나 팔렸나"를 분류(categoryName)까지
 * 스냅샷으로 남겨 상품별/분류별 매출·정산 집계를 가능하게 한다. 라인 금액 합계 = 헤더 saleAmount 가 항상 성립한다
 * (마지막 라인이 반올림 잔액을 흡수한다). 주문이 없는 순수 결제(결제 브릿지 테스트 등)에는 라인이 생성되지 않는다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "sales_transaction_line",
        indexes = {
                @Index(name = "idx_sales_line_header", columnList = "salesTransactionId"),
                @Index(name = "idx_sales_line_category", columnList = "businessDate,categoryName"),
                @Index(name = "idx_sales_line_product", columnList = "productId")
        }
)
public class SalesTransactionLine extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long salesTransactionId;

    private Long storeId;
    private Long orderId;
    private Long orderItemId;
    private Long productId;

    @Column(length = 200)
    private String productName;

    /** 주문 시점 분류명 스냅샷. 상품을 나중에 재분류해도 과거 집계가 흔들리지 않는다. 미분류면 "미분류". */
    @Column(nullable = false, length = 100)
    private String categoryName;

    @Column(length = 100)
    private String sku;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SaleType saleType;

    /** 부호 있는 금액 — CANCEL 라인은 음수. 헤더 saleAmount 와 부호 규약이 같다. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal lineAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal supplyAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal vatAmount;

    @Column(nullable = false)
    private LocalDate businessDate;

    @Column(nullable = false)
    private LocalDateTime occurredAt;

    @Column(nullable = false)
    private Boolean confirmedYn = false;

    public void confirm() {
        this.confirmedYn = true;
    }
}
