package com.yeni.backoffice.api.config;

import com.yeni.backoffice.core.commerce.service.CommerceDeliveryService;
import com.yeni.backoffice.core.payment.dto.PaymentDtos.SettlementBatchRunRequest;
import com.yeni.backoffice.core.payment.service.SettlementOperationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PortfolioOperationsSchedulerTest {

    private final CommerceDeliveryService deliveryService = mock(CommerceDeliveryService.class);
    private final SettlementOperationService settlementService = mock(SettlementOperationService.class);

    private PortfolioOperationsScheduler scheduler(boolean confirm, int grace, boolean draft) {
        return new PortfolioOperationsScheduler(deliveryService, settlementService, confirm, grace, draft);
    }

    @Test
    void autoConfirm_usesGraceDaysAsThreshold() {
        when(deliveryService.autoConfirmAgedDeliveries(any())).thenReturn(3);

        scheduler(true, 7, true).autoConfirmDeliveredOrders();

        ArgumentCaptor<LocalDateTime> captor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(deliveryService).autoConfirmAgedDeliveries(captor.capture());
        assertThat(captor.getValue()).isBefore(LocalDateTime.now().minusDays(6))
                .isAfter(LocalDateTime.now().minusDays(8));
    }

    @Test
    void autoConfirm_disabled_doesNothing() {
        scheduler(false, 7, true).autoConfirmDeliveredOrders();
        verifyNoInteractions(deliveryService);
    }

    @Test
    void autoDraft_runsSettlementForPreviousBusinessDay() {
        scheduler(true, 7, true).autoDraftSettlement();

        ArgumentCaptor<SettlementBatchRunRequest> captor = ArgumentCaptor.forClass(SettlementBatchRunRequest.class);
        verify(settlementService).runDailySettlement(captor.capture());
        assertThat(captor.getValue().targetDate()).isEqualTo(LocalDate.now().minusDays(1));
    }

    @Test
    void autoDraft_swallowsDuplicateExecutionException() {
        when(settlementService.runDailySettlement(any())).thenThrow(new IllegalStateException("이미 실행됨"));
        scheduler(true, 7, true).autoDraftSettlement(); // must not propagate
    }

    @Test
    void autoDraft_disabled_doesNothing() {
        scheduler(true, 7, false).autoDraftSettlement();
        verify(settlementService, never()).runDailySettlement(any());
    }
}
