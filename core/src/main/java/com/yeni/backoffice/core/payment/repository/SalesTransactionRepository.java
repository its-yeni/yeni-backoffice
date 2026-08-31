package com.yeni.backoffice.core.payment.repository;

import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.enums.LedgerStatus;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.enums.SalesSettlementStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SalesTransactionRepository extends JpaRepository<SalesTransaction, Long> {

    List<SalesTransaction> findByBusinessDateAndConfirmedYnTrueAndSettlementIncludedYnFalseOrderByIdAsc(LocalDate businessDate);

    List<SalesTransaction> findByBusinessDateAndStoreIdAndConfirmedYnTrueAndSettlementIncludedYnFalseOrderByIdAsc(LocalDate businessDate, Long storeId);

    List<SalesTransaction> findByBusinessDateBetweenOrderByOccurredAtDesc(LocalDate startDate, LocalDate endDate);

    Optional<SalesTransaction> findBySourceTypeAndSourceId(String sourceType, Long sourceId);

    Optional<SalesTransaction> findFirstByOrderNoAndSaleTypeOrderByIdAsc(String orderNo, SaleType saleType);
    Optional<SalesTransaction> findFirstByTidOrderByIdAsc(String tid);

    Optional<SalesTransaction> findFirstByTidAndSaleTypeOrderByIdAsc(String tid, SaleType saleType);

    List<SalesTransaction> findByPaymentIdOrderByIdAsc(Long paymentId);

    List<SalesTransaction> findByOrderNoOrderByIdAsc(String orderNo);

    @Query("""
            select s
            from SalesTransaction s
            where s.businessDate between :startDate and :endDate
              and (:storeId is null or s.storeId = :storeId)
              and (:saleType is null or s.saleType = :saleType)
              and (:ledgerStatus is null or s.ledgerStatus = :ledgerStatus)
              and (:settlementStatus is null or s.settlementStatus = :settlementStatus)
              and (:confirmedYn is null or s.confirmedYn = :confirmedYn)
              and (
                    :keyword is null
                    or lower(s.orderNo) like lower(concat('%', :keyword, '%'))
                    or lower(s.tid) like lower(concat('%', :keyword, '%'))
                    or lower(s.pgTransactionId) like lower(concat('%', :keyword, '%'))
                    or str(s.paymentId) like concat('%', :keyword, '%')
                    or str(s.cancelId) like concat('%', :keyword, '%')
                    or str(s.originalSalesTransactionId) like concat('%', :keyword, '%')
              )
            """)
    Page<SalesTransaction> searchLedger(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("storeId") Long storeId,
            @Param("saleType") SaleType saleType,
            @Param("ledgerStatus") LedgerStatus ledgerStatus,
            @Param("settlementStatus") SalesSettlementStatus settlementStatus,
            @Param("confirmedYn") Boolean confirmedYn,
            @Param("keyword") String keyword,
            Pageable pageable
    );

    @Query("""
            select new com.yeni.backoffice.core.payment.dto.PaymentDtos$SalesLedgerSummaryResponse(
              coalesce(sum(case when s.saleType = com.yeni.backoffice.core.payment.enums.SaleType.SALE then s.totalAmount else 0 end), 0),
              coalesce(sum(case when s.saleType = com.yeni.backoffice.core.payment.enums.SaleType.CANCEL then s.totalAmount else 0 end), 0),
              coalesce(sum(s.totalAmount), 0),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.NOT_SETTLED then 1 else null end),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.CALCULATED then 1 else null end),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.SETTLED then 1 else null end),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.PAID then 1 else null end),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.CARRIED_OVER then 1 else null end),
              count(case when s.settlementStatus = com.yeni.backoffice.core.payment.enums.SalesSettlementStatus.EXCLUDED then 1 else null end),
              count(case when s.confirmedYn = true then 1 else null end),
              count(case when s.confirmedYn = false then 1 else null end)
            )
            from SalesTransaction s
            where s.businessDate between :startDate and :endDate
              and (:storeId is null or s.storeId = :storeId)
              and (:saleType is null or s.saleType = :saleType)
              and (:ledgerStatus is null or s.ledgerStatus = :ledgerStatus)
              and (:settlementStatus is null or s.settlementStatus = :settlementStatus)
              and (:confirmedYn is null or s.confirmedYn = :confirmedYn)
              and (
                    :keyword is null
                    or lower(s.orderNo) like lower(concat('%', :keyword, '%'))
                    or lower(s.tid) like lower(concat('%', :keyword, '%'))
                    or lower(s.pgTransactionId) like lower(concat('%', :keyword, '%'))
                    or str(s.paymentId) like concat('%', :keyword, '%')
                    or str(s.cancelId) like concat('%', :keyword, '%')
                    or str(s.originalSalesTransactionId) like concat('%', :keyword, '%')
              )
            """)
    com.yeni.backoffice.core.payment.dto.PaymentDtos.SalesLedgerSummaryResponse summarizeLedger(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("storeId") Long storeId,
            @Param("saleType") SaleType saleType,
            @Param("ledgerStatus") LedgerStatus ledgerStatus,
            @Param("settlementStatus") SalesSettlementStatus settlementStatus,
            @Param("confirmedYn") Boolean confirmedYn,
            @Param("keyword") String keyword
    );
}
