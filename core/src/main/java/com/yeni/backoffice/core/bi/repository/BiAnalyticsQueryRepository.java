package com.yeni.backoffice.core.bi.repository;

import com.yeni.backoffice.core.bi.dto.BiDtos.BiFilter;
import com.yeni.backoffice.core.bi.dto.BiDtos.ChannelSalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.DailySalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.InventoryStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.PaymentStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.ProductSalesRow;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Power BI 리포트 전용 <b>조회 전용</b> 리포지토리. 운영용 Service/Repository 를 재사용하지 않고
 * 여기서 native SQL 로 {@code GROUP BY / SUM / COUNT} 집계까지 끝낸다.
 * 애플리케이션에서 전체 행을 읽어 합산하지 않는다.
 *
 * <p>매출 집계는 {@code sales_transaction_line}(SALE/CANCEL 원장의 상품 단위 분해)을 기준으로 한다.
 * CANCEL 라인의 {@code line_amount} 는 음수라 {@code net = SUM(line_amount)} 가 곧 순매출이다.
 */
@Repository
public class BiAnalyticsQueryRepository {

    private final EntityManager em;

    public BiAnalyticsQueryRepository(EntityManager em) {
        this.em = em;
    }

    /** 일자별 매출. category/channel 필터 지원. */
    public List<DailySalesRow> dailySales(BiFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.business_date,
                       COUNT(DISTINCT CASE WHEN l.sale_type = 'SALE' THEN s.order_no END)          AS order_count,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'SALE'   THEN l.line_amount END), 0)     AS gross_sales,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'CANCEL' THEN -l.line_amount END), 0)    AS cancel_amount,
                       COALESCE(SUM(CASE WHEN l.sale_type IN ('SALE','CANCEL') THEN l.line_amount END), 0) AS net_sales,
                       COUNT(DISTINCT CASE WHEN l.sale_type = 'SALE' THEN s.payment_id END)         AS payment_count
                FROM sales_transaction_line l
                JOIN sales_transaction s ON s.id = l.sales_transaction_id
                WHERE l.business_date BETWEEN :from AND :to
                """);
        appendSalesFilters(sql, filter);
        sql.append(" GROUP BY l.business_date ORDER BY l.business_date");

        Query query = em.createNativeQuery(sql.toString());
        bindSales(query, filter);

        List<DailySalesRow> rows = new ArrayList<>();
        for (Object[] r : rows(query)) {
            long orderCount = toLong(r[1]);
            BigDecimal net = toBigDecimal(r[4]);
            rows.add(new DailySalesRow(
                    toLocalDate(r[0]),
                    orderCount,
                    toBigDecimal(r[2]),
                    toBigDecimal(r[3]),
                    net,
                    toLong(r[5]),
                    orderCount == 0 ? BigDecimal.ZERO : net.divide(BigDecimal.valueOf(orderCount), 0, RoundingMode.HALF_UP)));
        }
        return rows;
    }

    /** 상품별 매출. */
    public List<ProductSalesRow> productSales(BiFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT l.product_id,
                       MAX(l.product_name)   AS product_name,
                       MAX(l.category_name)  AS category_name,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'SALE' THEN l.quantity ELSE 0 END), 0)  AS quantity,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'SALE'   THEN l.line_amount END), 0)     AS sales_amount,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'CANCEL' THEN -l.line_amount END), 0)    AS cancel_amount,
                       COALESCE(SUM(CASE WHEN l.sale_type IN ('SALE','CANCEL') THEN l.line_amount END), 0) AS net_sales
                FROM sales_transaction_line l
                JOIN sales_transaction s ON s.id = l.sales_transaction_id
                WHERE l.business_date BETWEEN :from AND :to
                  AND l.product_id IS NOT NULL
                """);
        appendSalesFilters(sql, filter);
        sql.append(" GROUP BY l.product_id ORDER BY net_sales DESC");

        Query query = em.createNativeQuery(sql.toString());
        bindSales(query, filter);

        List<ProductSalesRow> rows = new ArrayList<>();
        for (Object[] r : rows(query)) {
            rows.add(new ProductSalesRow(
                    toLong(r[0]),
                    r[1] == null ? "(이름 없음)" : r[1].toString(),
                    r[2] == null ? "미분류" : r[2].toString(),
                    toLong(r[3]),
                    toBigDecimal(r[4]),
                    toBigDecimal(r[5]),
                    toBigDecimal(r[6])));
        }
        return rows;
    }

    /** 판매채널(WEB/POS)별 매출. */
    public List<ChannelSalesRow> channelSales(BiFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT COALESCE(s.channel_type, 'WEB') AS channel_type,
                       COUNT(DISTINCT CASE WHEN l.sale_type = 'SALE' THEN s.order_no END)          AS order_count,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'SALE'   THEN l.line_amount END), 0)     AS sales_amount,
                       COALESCE(SUM(CASE WHEN l.sale_type = 'CANCEL' THEN -l.line_amount END), 0)    AS cancel_amount,
                       COALESCE(SUM(CASE WHEN l.sale_type IN ('SALE','CANCEL') THEN l.line_amount END), 0) AS net_sales
                FROM sales_transaction_line l
                JOIN sales_transaction s ON s.id = l.sales_transaction_id
                WHERE l.business_date BETWEEN :from AND :to
                """);
        if (filter.category() != null) {
            sql.append(" AND l.category_name = :category");
        }
        if (filter.channel() != null) {
            sql.append(" AND COALESCE(s.channel_type, 'WEB') = :channel");
        }
        sql.append(" GROUP BY COALESCE(s.channel_type, 'WEB') ORDER BY net_sales DESC");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("from", filter.from());
        query.setParameter("to", filter.to());
        if (filter.category() != null) {
            query.setParameter("category", filter.category());
        }
        if (filter.channel() != null) {
            query.setParameter("channel", filter.channel());
        }

        List<ChannelSalesRow> rows = new ArrayList<>();
        for (Object[] r : rows(query)) {
            rows.add(new ChannelSalesRow(
                    channelLabel(r[0] == null ? "WEB" : r[0].toString()),
                    toLong(r[1]),
                    toBigDecimal(r[2]),
                    toBigDecimal(r[3]),
                    toBigDecimal(r[4])));
        }
        return rows;
    }

    /** 상품 단위 재고 현황 — 옵션 SKU 재고를 상품으로 합산하고, LOT 유효기한 최솟값을 붙인다. */
    public List<InventoryStatusRow> inventoryStatus(BiFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.id,
                       p.product_name,
                       COALESCE(p.category, '미분류') AS category_name,
                       COALESCE(SUM(v.stock_quantity), 0)    AS on_hand,
                       COALESCE(SUM(v.reserved_quantity), 0) AS reserved,
                       COALESCE(SUM(CASE WHEN v.stock_quantity - v.reserved_quantity > 0
                                         THEN v.stock_quantity - v.reserved_quantity ELSE 0 END), 0) AS available,
                       COALESCE(SUM(v.safety_stock), 0)      AS safety,
                       (SELECT MIN(lot.expiration_date)
                          FROM inventory_lot lot
                          JOIN product_variant vv ON vv.id = lot.variant_id
                         WHERE vv.product_id = p.id
                           AND lot.available_quantity > 0
                           AND lot.expiration_date IS NOT NULL) AS nearest_exp
                FROM product p
                JOIN product_variant v ON v.product_id = p.id
                WHERE 1 = 1
                """);
        if (filter.category() != null) {
            sql.append(" AND p.category = :category");
        }
        sql.append(" GROUP BY p.id, p.product_name, p.category ORDER BY available ASC, p.id");

        Query query = em.createNativeQuery(sql.toString());
        if (filter.category() != null) {
            query.setParameter("category", filter.category());
        }

        List<InventoryStatusRow> rows = new ArrayList<>();
        for (Object[] r : rows(query)) {
            long available = toLong(r[5]);
            long safety = toLong(r[6]);
            rows.add(new InventoryStatusRow(
                    toLong(r[0]),
                    r[1] == null ? "(이름 없음)" : r[1].toString(),
                    r[2] == null ? "미분류" : r[2].toString(),
                    toLong(r[3]),
                    toLong(r[4]),
                    available,
                    safety,
                    available <= safety,
                    toLocalDate(r[7])));
        }
        return rows;
    }

    /** 결제 상태별 현황. APPROVE_UNKNOWN 등 결과불명 상태를 실제 enum 값 그대로 집계한다. */
    public List<PaymentStatusRow> paymentStatus(BiFilter filter) {
        StringBuilder sql = new StringBuilder("""
                SELECT pt.payment_status,
                       COUNT(*)                                                              AS payment_count,
                       COALESCE(SUM(pt.approved_amount), 0)                                   AS payment_amount,
                       COALESCE(SUM(CASE WHEN pt.canceled_amount > 0 THEN 1 ELSE 0 END), 0)   AS cancel_count,
                       COALESCE(SUM(pt.canceled_amount), 0)                                   AS cancel_amount,
                       COALESCE(SUM(CASE WHEN pt.payment_status IN
                             ('APPROVE_UNKNOWN','CANCEL_UNKNOWN','UNKNOWN','CANCEL_RECONCILE_REQUIRED',
                              'NETWORK_CANCEL_REQUIRED','NETWORK_CANCEL_FAILED')
                           THEN 1 ELSE 0 END), 0)                                             AS unknown_count
                FROM payment_transaction pt
                WHERE CAST(pt.approved_at AS DATE) BETWEEN :from AND :to
                """);
        if (filter.channel() != null) {
            sql.append(" AND COALESCE(pt.channel_type, 'WEB') = :channel");
        }
        sql.append(" GROUP BY pt.payment_status ORDER BY payment_count DESC");

        Query query = em.createNativeQuery(sql.toString());
        query.setParameter("from", filter.from());
        query.setParameter("to", filter.to());
        if (filter.channel() != null) {
            query.setParameter("channel", filter.channel());
        }

        List<PaymentStatusRow> rows = new ArrayList<>();
        for (Object[] r : rows(query)) {
            rows.add(new PaymentStatusRow(
                    r[0] == null ? "UNKNOWN" : r[0].toString(),
                    toLong(r[1]),
                    toBigDecimal(r[2]),
                    toLong(r[3]),
                    toBigDecimal(r[4]),
                    toLong(r[5])));
        }
        return rows;
    }

    // ---------- 공통 ----------

    private static void appendSalesFilters(StringBuilder sql, BiFilter filter) {
        if (filter.category() != null) {
            sql.append(" AND l.category_name = :category");
        }
        if (filter.channel() != null) {
            sql.append(" AND COALESCE(s.channel_type, 'WEB') = :channel");
        }
    }

    private static void bindSales(Query query, BiFilter filter) {
        query.setParameter("from", filter.from());
        query.setParameter("to", filter.to());
        if (filter.category() != null) {
            query.setParameter("category", filter.category());
        }
        if (filter.channel() != null) {
            query.setParameter("channel", filter.channel());
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object[]> rows(Query query) {
        return query.getResultList();
    }

    private static String channelLabel(String channelType) {
        return "POS".equalsIgnoreCase(channelType) ? "매장" : "온라인";
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.parseLong(value.toString().trim());
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        return new BigDecimal(value.toString().trim());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof java.util.Date date) {
            return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
        }
        return LocalDate.parse(value.toString().substring(0, 10));
    }
}
