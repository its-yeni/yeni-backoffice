package com.yeni.backoffice.core.payment.insight;

import com.yeni.backoffice.core.payment.insight.command.FailureInsightCommand;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightItem;
import com.yeni.backoffice.core.payment.insight.result.FailureInsightResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** LLM의 구조화 응답을 입력 표본과 대조해 허위 참조키와 중복 집계를 제거한다. */
@Component
public class FailureInsightResultValidator {

    static final int MAX_TEXT_LENGTH = 300;

    public FailureInsightResult validate(FailureInsightResult result, FailureInsightCommand command) {
        Set<String> allowedRefKeys = new HashSet<>();
        command.entries().forEach(entry -> {
            if (entry.refKey() != null && !entry.refKey().isBlank()) {
                allowedRefKeys.add(entry.refKey());
            }
        });

        Set<String> assignedRefKeys = new HashSet<>();
        List<FailureInsightItem> validatedItems = new ArrayList<>();
        for (FailureInsightItem item : result.items() == null ? List.<FailureInsightItem>of() : result.items()) {
            List<String> validRefs = new ArrayList<>();
            List<String> itemRefKeys = item.relatedRefKeys() == null ? List.of() : item.relatedRefKeys();
            for (String refKey : new LinkedHashSet<>(itemRefKeys)) {
                if (allowedRefKeys.contains(refKey) && assignedRefKeys.add(refKey)) {
                    validRefs.add(refKey);
                }
            }
            if (validRefs.isEmpty()) {
                continue;
            }
            validatedItems.add(new FailureInsightItem(
                    item.category(),
                    bounded(item.cause()),
                    validRefs.size(),
                    item.severity(),
                    bounded(item.suggestedAction()),
                    List.copyOf(validRefs)
            ));
        }

        return new FailureInsightResult(
                bounded(result.summaryHeadline()),
                List.copyOf(validatedItems),
                result.provider(),
                result.modelUsed(),
                result.generatedAt(),
                result.windowFrom(),
                result.windowTo(),
                result.sampleSize(),
                result.totalCandidateCount()
        );
    }

    private String bounded(String value) {
        if (value == null || value.length() <= MAX_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_TEXT_LENGTH) + "…";
    }
}
