package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.entity.ChartOfAccount;
import com.yeni.backoffice.core.payment.repository.ChartOfAccountRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 애플리케이션 기동 시 계정과목 마스터를 표준 정의로 upsert 한다. (AdminNavigationDataInitializer와 같은 패턴) */
@Component
@Order(20)
public class GlChartInitializer implements CommandLineRunner {

    private final ChartOfAccountRepository accounts;

    public GlChartInitializer(ChartOfAccountRepository accounts) {
        this.accounts = accounts;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (GlAccounts.AccountDef def : GlAccounts.DEFINITIONS) {
            accounts.findByCode(def.code())
                    .ifPresentOrElse(
                            existing -> existing.update(def.name(), def.type(), def.sortOrder(), true),
                            () -> accounts.save(ChartOfAccount.builder()
                                    .code(def.code())
                                    .name(def.name())
                                    .accountType(def.type())
                                    .sortOrder(def.sortOrder())
                                    .active(true)
                                    .build()));
        }
    }
}
