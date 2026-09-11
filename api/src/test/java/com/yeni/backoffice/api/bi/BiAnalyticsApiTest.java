package com.yeni.backoffice.api.bi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BI API(/api/bi/**)는 Power BI 가 읽어가는 flat 한 집계 배열을 반환한다.
 * 금액은 숫자로, 결제 상태는 실제 enum 값으로 나와야 한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class BiAnalyticsApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void allBiEndpointsReturnFlatArrays() throws Exception {
        for (String path : new String[]{
                "/api/bi/sales/daily", "/api/bi/sales/products", "/api/bi/sales/stores",
                "/api/bi/inventory/status", "/api/bi/payment/status"}) {
            mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Test
    void dailySalesExposesNetSalesAndAverageOrderValueAsNumbers() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bi/sales/daily")
                        .param("from", "2020-01-01").param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].date").exists())
                .andExpect(jsonPath("$[0].orderCount").isNumber())
                .andExpect(jsonPath("$[0].netSales").isNumber())
                .andExpect(jsonPath("$[0].averageOrderValue").isNumber())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("원\"");
    }

    @Test
    void productSalesGroupsByProductWithCategorySnapshot() throws Exception {
        mockMvc.perform(get("/api/bi/sales/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").isNumber())
                .andExpect(jsonPath("$[0].categoryName").exists())
                .andExpect(jsonPath("$[0].quantity").isNumber())
                .andExpect(jsonPath("$[0].netSales").isNumber());
    }

    @Test
    void channelSalesUsesRealChannelDimension() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bi/sales/stores"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(rows.isArray()).isTrue();
        for (JsonNode row : rows) {
            assertThat(row.get("channelName").asText()).isIn("온라인", "매장");
        }
    }

    @Test
    void inventoryStatusExposesAvailableSafetyAndShortageFlag() throws Exception {
        mockMvc.perform(get("/api/bi/inventory/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].productId").isNumber())
                .andExpect(jsonPath("$[0].availableQuantity").isNumber())
                .andExpect(jsonPath("$[0].safetyStock").isNumber())
                .andExpect(jsonPath("$[0].shortageYn").isBoolean());
    }

    @Test
    void paymentStatusReflectsUnknownResultStates() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/bi/payment/status")
                        .param("from", "2020-01-01").param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString());
        boolean hasUnknown = false;
        for (JsonNode row : rows) {
            assertThat(row.get("paymentCount").isNumber()).isTrue();
            assertThat(row.get("paymentAmount").isNumber()).isTrue();
            if (row.get("paymentStatus").asText().contains("UNKNOWN")) {
                hasUnknown = true;
            }
        }
        assertThat(hasUnknown).as("데모 시드에는 APPROVE_UNKNOWN 결제가 포함된다").isTrue();
    }

    @Test
    void rangeIsClampedToOneYear() throws Exception {
        // from 을 10년 전으로 줘도 서비스가 최대 366일로 좁힌다 — 500 없이 정상 응답.
        mockMvc.perform(get("/api/bi/sales/daily")
                        .param("from", LocalDate.now().minusYears(10).toString())
                        .param("to", LocalDate.now().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
