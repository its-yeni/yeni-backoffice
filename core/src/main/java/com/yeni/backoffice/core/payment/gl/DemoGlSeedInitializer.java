package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.dto.GlDtos.PostResult;
import com.yeni.backoffice.core.payment.repository.JournalEntryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 데모/시연 프로파일에서 회계 화면이 빈 상태로 뜨지 않도록, 기동이 끝난 뒤 이미 시딩된 매출 원장·정산 명세를
 * 한 번 분개로 전기한다. 전기는 멱등하므로 재기동해도 안전하고, 이후에는 스케줄러가 이어받는다.
 *
 * <p>{@link ApplicationReadyEvent} 시점에 돌린다 — CommandLineRunner 단계에서 다른 데모 시더들과 뒤섞여
 * 메모리가 빠듯한 환경(fly.io 512MB)에서 실패하던 것을, 컨텍스트가 완전히 준비된 뒤로 미룬다.
 */
@Component
@Profile("fly | demo")
public class DemoGlSeedInitializer {

    private static final Logger log = LoggerFactory.getLogger(DemoGlSeedInitializer.class);

    private final GlPostingService glPostingService;
    private final JournalEntryRepository journalEntryRepository;

    public DemoGlSeedInitializer(GlPostingService glPostingService, JournalEntryRepository journalEntryRepository) {
        this.glPostingService = glPostingService;
        this.journalEntryRepository = journalEntryRepository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedJournalEntries() {
        try {
            if (journalEntryRepository.count() > 0) {
                return;
            }
            PostResult result = glPostingService.postPending();
            log.info("데모 분개 전기 완료: 매출 {}건 / 정산 {}건", result.createdFromSales(), result.createdFromSettlements());
        } catch (Throwable t) {
            log.warn("데모 분개 전기 실패 — 회계 화면 첫 진입 시 자동 재시도되거나 '미전기 분개 생성' 버튼으로 전기할 수 있습니다.", t);
        }
    }
}
