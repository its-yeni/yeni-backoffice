package com.yeni.backoffice.api.analytics.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 분석 화면. 데이터는 프론트에서 {@code /api/analytics/**} 를 호출해 렌더링한다(서버 렌더링 최소화).
 * 업무 처리(수정/삭제/승인)는 넣지 않고 현황·추이·이상 데이터 확인에만 집중한다.
 */
@Controller
public class AnalyticsViewController {

    @GetMapping("/admin/analytics")
    public String overview() {
        return "analytics/overview";
    }

    @GetMapping("/admin/analytics/orders")
    public String orders() {
        return "analytics/orders";
    }

    @GetMapping("/admin/analytics/payments")
    public String payments() {
        return "analytics/payments";
    }

    @GetMapping("/admin/analytics/settlements")
    public String settlements() {
        return "analytics/settlements";
    }

    @GetMapping("/admin/analytics/inventory")
    public String inventory() {
        return "analytics/inventory";
    }
}
