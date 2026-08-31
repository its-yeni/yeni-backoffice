package com.yeni.backoffice.api.analytics;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AnalyticsApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void overviewReturnsFlatKpiWithNumericAmounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderCount").exists())
                .andExpect(jsonPath("$.paymentApprovalRate").exists())
                .andExpect(jsonPath("$.settlementMismatchCount").exists())
                .andReturn();
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.get("orderAmount").isNumber()).isTrue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("원\"");
    }

    @Test
    void listEndpointsReturnFlatArrays() throws Exception {
        for (String resource : new String[]{"orders", "payments", "settlements", "inventory"}) {
            mockMvc.perform(get("/api/analytics/" + resource))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
        }
    }

    @Test
    void ordersEndpointExposesDateChannelStoreAndAmounts() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/analytics/orders")
                        .param("startDate", "2020-01-01").param("endDate", "2100-12-31"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode rows = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(rows.isArray()).isTrue();
        if (!rows.isEmpty()) {
            JsonNode row = rows.get(0);
            assertThat(row.hasNonNull("date")).isTrue();
            assertThat(row.hasNonNull("channel")).isTrue();
            assertThat(row.hasNonNull("storeName")).isTrue();
            assertThat(row.get("orderAmount").isNumber()).isTrue();
        }
    }

    @Test
    void overviewOrderCountMatchesOrdersEndpointSum() throws Exception {
        String params = "?startDate=2020-01-01&endDate=2100-12-31";
        JsonNode overview = objectMapper.readTree(mockMvc.perform(get("/api/analytics/overview" + params))
                .andReturn().getResponse().getContentAsString());
        JsonNode orders = objectMapper.readTree(mockMvc.perform(get("/api/analytics/orders" + params))
                .andReturn().getResponse().getContentAsString());
        long sum = 0;
        for (JsonNode row : orders) sum += row.get("orderCount").asLong();
        assertThat(overview.get("orderCount").asLong()).isEqualTo(sum);
    }

    @Test
    void analyticsApiIsSeparateFromAdminApi() throws Exception {
        mockMvc.perform(get("/api/analytics/inventory"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"));
    }
}
