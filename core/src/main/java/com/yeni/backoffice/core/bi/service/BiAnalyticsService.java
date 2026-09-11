package com.yeni.backoffice.core.bi.service;

import com.yeni.backoffice.core.bi.dto.BiDtos.BiFilter;
import com.yeni.backoffice.core.bi.dto.BiDtos.ChannelSalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.DailySalesRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.InventoryStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.PaymentStatusRow;
import com.yeni.backoffice.core.bi.dto.BiDtos.ProductSalesRow;
import com.yeni.backoffice.core.bi.repository.BiAnalyticsQueryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Power BI 리포트용 집계 조회 서비스. 운영 도메인 서비스를 재사용하지 않고
 * {@link BiAnalyticsQueryRepository}(native SQL 집계)만 호출한다. 페이지네이션 없이 집계 결과 전체를 반환하되,
 * 조회 기간은 {@link BiFilter}에서 최대 366일로 제한한다.
 */
@Service
@Transactional(readOnly = true)
public class BiAnalyticsService {

    private final BiAnalyticsQueryRepository repository;

    public BiAnalyticsService(BiAnalyticsQueryRepository repository) {
        this.repository = repository;
    }

    public BiFilter filter(LocalDate from, LocalDate to, String category, String channel) {
        return new BiFilter(from, to, category, channel);
    }

    public List<DailySalesRow> dailySales(BiFilter filter) {
        return repository.dailySales(filter);
    }

    public List<ProductSalesRow> productSales(BiFilter filter) {
        return repository.productSales(filter);
    }

    public List<ChannelSalesRow> channelSales(BiFilter filter) {
        return repository.channelSales(filter);
    }

    public List<InventoryStatusRow> inventoryStatus(BiFilter filter) {
        return repository.inventoryStatus(filter);
    }

    public List<PaymentStatusRow> paymentStatus(BiFilter filter) {
        return repository.paymentStatus(filter);
    }
}
