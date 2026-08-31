package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface SalesTransactionLineRepository extends JpaRepository<SalesTransactionLine, Long> {

    List<SalesTransactionLine> findBySalesTransactionId(Long salesTransactionId);

    List<SalesTransactionLine> findBySalesTransactionIdInOrderByIdAsc(Collection<Long> salesTransactionIds);

    boolean existsBySalesTransactionId(Long salesTransactionId);

    /**
     * 분류별 매출 집계. 행: [categoryName, 판매수량합, 매출액합(부호포함), 라인수].
     * lineAmount 가 부호를 담으므로 SALE − CANCEL 이 자연히 상계된다.
     */
    @Query("""
            select l.categoryName,
                   sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.quantity else -l.quantity end),
                   sum(l.lineAmount),
                   count(l)
              from SalesTransactionLine l
             where l.businessDate between :start and :end
               and (:storeId is null or l.storeId = :storeId)
               and (:confirmedYn is null or l.confirmedYn = :confirmedYn)
             group by l.categoryName
             order by sum(l.lineAmount) desc
            """)
    List<Object[]> aggregateByCategory(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("storeId") Long storeId,
            @Param("confirmedYn") Boolean confirmedYn);

    /** 일자별 매출 집계. 행: [businessDate, SALE 라인합, CANCEL 라인합(음수), 주문수(SALE 기준 distinct)]. */
    @Query("""
            select l.businessDate,
                   sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.lineAmount else 0 end),
                   sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.CANCEL then l.lineAmount else 0 end),
                   count(distinct case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.orderId else null end)
              from SalesTransactionLine l
             where l.businessDate between :start and :end
               and (:storeId is null or l.storeId = :storeId)
               and (:confirmedYn is null or l.confirmedYn = :confirmedYn)
             group by l.businessDate
             order by l.businessDate
            """)
    List<Object[]> aggregateDaily(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("storeId") Long storeId,
            @Param("confirmedYn") Boolean confirmedYn);

    /** 매출 요약. 행: [SALE 라인합, CANCEL 라인합(음수), 주문수(SALE 기준 distinct)]. */
    @Query("""
            select coalesce(sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.lineAmount else 0 end), 0),
                   coalesce(sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.CANCEL then l.lineAmount else 0 end), 0),
                   count(distinct case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.orderId else null end)
              from SalesTransactionLine l
             where l.businessDate between :start and :end
               and (:storeId is null or l.storeId = :storeId)
               and (:confirmedYn is null or l.confirmedYn = :confirmedYn)
            """)
    List<Object[]> summarize(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("storeId") Long storeId,
            @Param("confirmedYn") Boolean confirmedYn);

    /** 상품별 매출 집계. 행: [productId, productName, 판매수량합, 매출액합, 라인수]. */
    @Query("""
            select l.productId, max(l.productName),
                   sum(case when l.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then l.quantity else -l.quantity end),
                   sum(l.lineAmount),
                   count(l)
              from SalesTransactionLine l
             where l.businessDate between :start and :end
               and (:storeId is null or l.storeId = :storeId)
               and (:confirmedYn is null or l.confirmedYn = :confirmedYn)
             group by l.productId
             order by sum(l.lineAmount) desc
            """)
    List<Object[]> aggregateByProduct(
            @Param("start") LocalDate start,
            @Param("end") LocalDate end,
            @Param("storeId") Long storeId,
            @Param("confirmedYn") Boolean confirmedYn);
}
