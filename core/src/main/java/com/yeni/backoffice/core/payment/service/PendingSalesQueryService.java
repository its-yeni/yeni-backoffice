package com.yeni.backoffice.core.payment.service;

import com.yeni.backoffice.core.commerce.entity.CommerceDelivery;
import com.yeni.backoffice.core.commerce.entity.CommerceOrder;
import com.yeni.backoffice.core.commerce.enums.DeliveryStatus;
import com.yeni.backoffice.core.commerce.repository.CommerceDeliveryRepository;
import com.yeni.backoffice.core.commerce.repository.CommerceOrderRepository;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.PendingSalesResponse;
import com.yeni.backoffice.core.payment.dto.SalesAnalyticsDtos.PendingSalesRow;
import com.yeni.backoffice.core.payment.entity.SalesTransaction;
import com.yeni.backoffice.core.payment.entity.SalesTransactionLine;
import com.yeni.backoffice.core.payment.enums.SaleType;
import com.yeni.backoffice.core.payment.repository.SalesTransactionLineRepository;
import com.yeni.backoffice.core.payment.repository.SalesTransactionRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class PendingSalesQueryService {
    private final SalesTransactionRepository salesRepository;
    private final SalesTransactionLineRepository salesLineRepository;
    private final CommerceOrderRepository orderRepository;
    private final CommerceDeliveryRepository deliveryRepository;

    public PendingSalesQueryService(SalesTransactionRepository salesRepository,
                                    SalesTransactionLineRepository salesLineRepository,
                                    CommerceOrderRepository orderRepository,
                                    CommerceDeliveryRepository deliveryRepository) {
        this.salesRepository = salesRepository;
        this.salesLineRepository = salesLineRepository;
        this.orderRepository = orderRepository;
        this.deliveryRepository = deliveryRepository;
    }

    public PendingSalesResponse getPendingSales(LocalDate startDate, LocalDate endDate, Long storeId, String keyword) {
        LocalDate start = startDate == null ? LocalDate.now().minusDays(30) : startDate;
        LocalDate end = endDate == null ? LocalDate.now() : endDate;
        var pageable = PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "occurredAt", "id"));
        List<SalesTransaction> headers = salesRepository.searchLedger(start, end, storeId, SaleType.SALE,
                        null, null, Boolean.FALSE, normalize(keyword), pageable).getContent().stream()
                .filter(header -> !Boolean.TRUE.equals(header.getSettlementIncludedYn())).toList();

        Map<Long, List<SalesTransactionLine>> linesByHeader = salesLineRepository
                .findBySalesTransactionIdInOrderByIdAsc(headers.stream().map(SalesTransaction::getId).toList())
                .stream().collect(Collectors.groupingBy(SalesTransactionLine::getSalesTransactionId));
        Map<String, List<CommerceDelivery>> deliveriesByOrder = deliveriesByOrder(headers);

        List<PendingSalesRow> rows = headers.stream().map(header -> toRow(header,
                linesByHeader.getOrDefault(header.getId(), List.of()),
                deliveriesByOrder.getOrDefault(header.getOrderNo(), List.of()))).toList();
        BigDecimal total = rows.stream().map(PendingSalesRow::saleAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PendingSalesResponse(start, end, total, rows.size(), rows);
    }

    private Map<String, List<CommerceDelivery>> deliveriesByOrder(List<SalesTransaction> headers) {
        List<String> orderNos = headers.stream().map(SalesTransaction::getOrderNo).distinct().toList();
        Map<Long, String> orderNoById = orderRepository.findByOrderNoIn(orderNos).stream()
                .collect(Collectors.toMap(CommerceOrder::getId, CommerceOrder::getOrderNo));
        Map<String, List<CommerceDelivery>> result = new HashMap<>();
        if (!orderNoById.isEmpty()) {
            deliveryRepository.findByOrderIdIn(orderNoById.keySet()).forEach(delivery ->
                    result.computeIfAbsent(orderNoById.get(delivery.getOrderId()), key -> new ArrayList<>()).add(delivery));
        }
        return result;
    }

    private PendingSalesRow toRow(SalesTransaction header, List<SalesTransactionLine> lines,
                                  List<CommerceDelivery> deliveries) {
        List<String> categories = lines.stream().map(SalesTransactionLine::getCategoryName).distinct().toList();
        List<String> names = lines.stream().map(SalesTransactionLine::getProductName).filter(Objects::nonNull).distinct().toList();
        String productSummary = names.isEmpty() ? "-" : names.size() == 1 ? names.get(0) : names.get(0) + " 외 " + (names.size() - 1) + "건";
        boolean confirmable = !deliveries.isEmpty()
                && deliveries.stream().allMatch(value -> value.getStatus() == DeliveryStatus.DELIVERED || value.getStatus() == DeliveryStatus.RETURNED)
                && deliveries.stream().anyMatch(value -> value.getStatus() == DeliveryStatus.DELIVERED);
        return new PendingSalesRow(header.getId(), header.getOrderNo(), header.getOccurredAt(), header.getPaymentId(),
                header.getTid(), categories.isEmpty() ? "미분류" : String.join(", ", categories), productSummary,
                lines.size(), header.getSaleAmount(), header.getSettlementStatus().name(), deliverySummary(deliveries), confirmable);
    }

    private String deliverySummary(List<CommerceDelivery> deliveries) {
        if (deliveries.isEmpty()) return "배송 정보 없음";
        if (deliveries.stream().allMatch(value -> value.getStatus() == DeliveryStatus.DELIVERED)) return "배송 완료";
        if (deliveries.stream().anyMatch(value -> value.getStatus() == DeliveryStatus.PREPARING)) return "배송 준비";
        if (deliveries.stream().anyMatch(value -> value.getStatus() == DeliveryStatus.IN_TRANSIT)) return "배송 중";
        if (deliveries.stream().anyMatch(value -> value.getStatus() == DeliveryStatus.RETURNED)) return "일부 반송";
        return "진행 중";
    }

    private String normalize(String keyword) {
        return StringUtils.hasText(keyword) ? keyword.trim() : null;
    }
}
