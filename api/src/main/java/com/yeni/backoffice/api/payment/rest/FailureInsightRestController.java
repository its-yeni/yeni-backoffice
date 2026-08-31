package com.yeni.backoffice.api.payment.rest;

import com.yeni.backoffice.core.payment.dto.InsightDtos.FailureInsightResponse;
import com.yeni.backoffice.core.payment.config.PortfolioInsightProperties;
import com.yeni.backoffice.core.payment.insight.FailureInsightService;
import com.yeni.backoffice.core.payment.insight.FailureInsightRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/api/insight")
@Tag(name = "Failure Insight", description = "실패 로그 AI 원인 요약 API")
public class FailureInsightRestController {

    private final FailureInsightService failureInsightService;
    private final FailureInsightRouter failureInsightRouter;
    private final PortfolioInsightProperties properties;

    public FailureInsightRestController(
            FailureInsightService failureInsightService,
            FailureInsightRouter failureInsightRouter,
            PortfolioInsightProperties properties) {
        this.failureInsightService = failureInsightService;
        this.failureInsightRouter = failureInsightRouter;
        this.properties = properties;
    }

    @GetMapping("/failure-summary")
    @Operation(
            summary = "실패 로그 AI 원인 요약",
            description = "최근 PG API 실패 로그와 RecoveryTask를 원인별로 그룹핑해 요약합니다. "
                    + "운영자의 1차 참고용이며, 실제 재시도/취소 처리는 여기서 수행하지 않습니다."
    )
    public ResponseEntity<FailureInsightResponse> failureSummary() {
        boolean apiKeyConfigured = properties.getOpenai().getApiKey() != null
                && !properties.getOpenai().getApiKey().isBlank();
        return ResponseEntity.ok(FailureInsightResponse.from(
                failureInsightService.getFailureInsight(),
                failureInsightRouter.route(),
                apiKeyConfigured
        ));
    }
}
