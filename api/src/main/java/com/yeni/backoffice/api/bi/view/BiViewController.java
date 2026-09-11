package com.yeni.backoffice.api.bi.view;

import com.yeni.backoffice.api.bi.config.BiReportProperties;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * "데이터 분석" 화면. 운영 대시보드가 "지금 처리할 운영 이슈"라면 이 화면은
 * 기간·상품·카테고리 단위 집계를 Power BI 로 분석한 결과를 보여주는 리포트 영역이다.
 * 데이터는 {@code /api/bi/**} 로 제공하고, 이 화면은 리포트 embed 영역과 설계 설명만 렌더링한다.
 */
@Controller
public class BiViewController {

    private final BiReportProperties properties;

    public BiViewController(BiReportProperties properties) {
        this.properties = properties;
    }

    @GetMapping("/admin/data-analysis")
    public String dataAnalysis(Model model) {
        model.addAttribute("biEmbedUrl", properties.getEmbedUrl());
        model.addAttribute("biHasEmbed", properties.hasEmbedUrl());
        return "bi/data-analysis";
    }
}
