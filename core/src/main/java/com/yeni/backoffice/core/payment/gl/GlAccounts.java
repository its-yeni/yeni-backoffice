package com.yeni.backoffice.core.payment.gl;

import com.yeni.backoffice.core.payment.enums.AccountType;

import java.util.List;

/**
 * 이 포트폴리오가 사용하는 계정과목 정의. 코드/이름은 한국 표준 계정과목표를 근사한다.
 * 실제 ERP는 회사별로 계정 체계를 커스터마이즈하지만, 여기서는 결제→정산 흐름에 필요한 최소 계정만 고정한다.
 */
public final class GlAccounts {

    private GlAccounts() {
    }

    public static final String BANK = "103";              // 보통예금
    public static final String RECEIVABLE = "108";         // 미수금 (PG 정산 예정액)
    public static final String VAT_INPUT = "135";          // 부가세대급금 (매입세액 — PG 수수료 VAT)
    public static final String VAT_OUTPUT = "255";         // 부가세예수금 (매출세액)
    public static final String SALES = "401";              // 상품매출
    public static final String SALES_RETURN = "403";       // 매출환입및에누리 (매출 차감)
    public static final String PG_FEE = "831";             // 지급수수료 (PG 결제대행 수수료)
    public static final String SETTLEMENT_ADJ = "930";     // 정산조정 (유보·조정 차액 흡수)

    public record AccountDef(String code, String name, AccountType type, int sortOrder) {
    }

    public static final List<AccountDef> DEFINITIONS = List.of(
            new AccountDef(BANK, "보통예금", AccountType.ASSET, 10),
            new AccountDef(RECEIVABLE, "미수금", AccountType.ASSET, 20),
            new AccountDef(VAT_INPUT, "부가세대급금", AccountType.ASSET, 30),
            new AccountDef(VAT_OUTPUT, "부가세예수금", AccountType.LIABILITY, 40),
            new AccountDef(SALES, "상품매출", AccountType.REVENUE, 50),
            new AccountDef(SALES_RETURN, "매출환입및에누리", AccountType.REVENUE, 60),
            new AccountDef(PG_FEE, "지급수수료", AccountType.EXPENSE, 70),
            new AccountDef(SETTLEMENT_ADJ, "정산조정", AccountType.EXPENSE, 80)
    );
}
