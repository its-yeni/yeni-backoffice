package com.yeni.backoffice.core.bi.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Power BI 리포트가 조회하는 <b>분석용 집계 DTO</b> 모음.
 * 운영 화면 DTO(개별 건 처리용)와 분리한다. 집계는 SQL에서 끝내고, 여기서는 flat 한 행으로만 받는다.
 */
public final class BiDtos {

    private BiDtos() {
    }

    /**
     * BI 조회 조건. 기간은 필수(미지정 시 최근 90일), 최대 366일로 제한한다.
     * {@code category} / {@code channel} 은 선택.
     */
    public record BiFilter(LocalDate from, LocalDate to, String category, String channel) {

        public static final int MAX_RANGE_DAYS = 366;

        public BiFilter {
            LocalDate today = LocalDate.now();
            if (to == null) {
                to = today;
            }
            if (from == null) {
                from = to.minusDays(90);
            }
            if (from.isAfter(to)) {
                LocalDate swap = from;
                from = to;
                to = swap;
            }
            if (from.isBefore(to.minusDays(MAX_RANGE_DAYS))) {
                from = to.minusDays(MAX_RANGE_DAYS);
            }
            category = blankToNull(category);
            channel = blankToNull(channel);
        }

        private static String blankToNull(String value) {
            return value == null || value.isBlank() ? null : value.trim();
        }
    }

    /** GET /api/bi/sales/daily — 일자별 매출. */
    public record DailySalesRow(
            LocalDate date,
            long orderCount,
            BigDecimal grossSales,
            BigDecimal cancelAmount,
            BigDecimal netSales,
            long paymentCount,
            BigDecimal averageOrderValue) {
    }

    /** GET /api/bi/sales/products — 상품별 매출. */
    public record ProductSalesRow(
            Long productId,
            String productName,
            String categoryName,
            long quantity,
            BigDecimal salesAmount,
            BigDecimal cancelAmount,
            BigDecimal netSales) {
    }

    /** GET /api/bi/sales/stores — 판매채널(WEB 온라인 / POS 매장)별 매출. */
    public record ChannelSalesRow(
            String channelName,
            long orderCount,
            BigDecimal salesAmount,
            BigDecimal cancelAmount,
            BigDecimal netSales) {
    }

    /** GET /api/bi/inventory/status — 상품 단위 재고 현황(가용재고·안전재고·LOT 유효기간). */
    public record InventoryStatusRow(
            Long productId,
            String productName,
            String categoryName,
            long onHandQuantity,
            long reservedQuantity,
            long availableQuantity,
            long safetyStock,
            boolean shortageYn,
            LocalDate nearestExpirationDate) {
    }

    /** GET /api/bi/payment/status — 결제 상태별 운영 현황(APPROVE_UNKNOWN 등 결과불명 포함). */
    public record PaymentStatusRow(
            String paymentStatus,
            long paymentCount,
            BigDecimal paymentAmount,
            long cancelCount,
            BigDecimal cancelAmount,
            long unknownCount) {
    }
}
