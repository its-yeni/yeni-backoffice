package com.yeni.backoffice.api.bi.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * "데이터 분석" 화면에 넣을 Power BI 리포트 링크. 실제 embed URL 이 준비되기 전에는 비워 두고,
 * 화면은 placeholder 만 노출한다. {@code BI_REPORT_EMBED_URL} 환경변수로 주입한다.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "bi.report")
public class BiReportProperties {

    /** Power BI publish-to-web / embed iframe URL. 비어 있으면 화면은 placeholder 를 보여준다. */
    private String embedUrl = "";

    public boolean hasEmbedUrl() {
        return embedUrl != null && !embedUrl.isBlank();
    }
}
