package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.enums.InsightProvider;
import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;

/**
 * 실패 로그 묶음을 원인별로 요약하는 트리아지 보조 기능.
 * 여기서 만든 결과는 운영자의 1차 참고용이며, 실제 재시도/취소 같은 처리에는 관여하지 않는다.
 */
public interface FailureInsightProvider {

    InsightProvider provider();

    FailureInsightResult summarize(FailureInsightCommand command);
}
