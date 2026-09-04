package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.entity.ChartOfAccount;
import com.yeni.backoffice.core.payment.enums.AccountType;
import com.yeni.backoffice.core.payment.repository.ChartOfAccountRepository;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** 계정과목 코드 → 이름/유형 조회. 마스터가 비어 있어도(테스트 등) {@link GlAccounts} 정의로 폴백한다. */
@Component
public class AccountNameResolver {

    private final ChartOfAccountRepository accounts;

    public AccountNameResolver(ChartOfAccountRepository accounts) {
        this.accounts = accounts;
    }

    public String name(String code) {
        return accounts.findByCode(code)
                .map(ChartOfAccount::getName)
                .orElseGet(() -> fallbackDef(code).name());
    }

    public AccountType type(String code) {
        return accounts.findByCode(code)
                .map(ChartOfAccount::getAccountType)
                .orElseGet(() -> fallbackDef(code).type());
    }

    /** 마스터 전체를 코드 순서대로. 마스터가 비어 있으면 표준 정의를 그대로 돌려준다. */
    public Map<String, GlAccounts.AccountDef> allByCode() {
        Map<String, GlAccounts.AccountDef> map = new LinkedHashMap<>();
        var stored = accounts.findAllByOrderBySortOrderAsc();
        if (stored.isEmpty()) {
            GlAccounts.DEFINITIONS.forEach(def -> map.put(def.code(), def));
            return map;
        }
        stored.forEach(account -> map.put(account.getCode(),
                new GlAccounts.AccountDef(account.getCode(), account.getName(), account.getAccountType(), account.getSortOrder())));
        return map;
    }

    private GlAccounts.AccountDef fallbackDef(String code) {
        return GlAccounts.DEFINITIONS.stream()
                .filter(def -> def.code().equals(code))
                .findFirst()
                .orElse(new GlAccounts.AccountDef(code, code, AccountType.ASSET, 999));
    }
}
