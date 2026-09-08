package com.yeni.backoffice.core.admin.navigation.init;

import com.yeni.backoffice.core.admin.auth.AdminRole;
import com.yeni.backoffice.core.admin.navigation.entity.AdminNavigationGroup;
import com.yeni.backoffice.core.admin.navigation.entity.AdminNavigationItem;
import com.yeni.backoffice.core.admin.navigation.repository.AdminNavigationGroupRepository;
import com.yeni.backoffice.core.admin.navigation.repository.AdminNavigationItemRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/**
 * 관리자 내비게이션 시드.
 * <p>사이드바에는 <b>업무 흐름 단위 9개</b>만 노출({@code displayYn=true})하고,
 * 나머지 화면은 라우트·기능을 유지한 채 "전체 기능" 페이지에서만 접근하도록
 * {@code displayYn=false} 로 둔다({@code useYn} 은 모두 true).
 */
@Component
public class AdminNavigationDataInitializer implements CommandLineRunner {

    private final AdminNavigationGroupRepository groups;
    private final AdminNavigationItemRepository items;

    public AdminNavigationDataInitializer(AdminNavigationGroupRepository groups, AdminNavigationItemRepository items) {
        this.groups = groups;
        this.items = items;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 살아 있는 화면 URL — 여기 없는 항목은 소프트 삭제
        Set<String> alive = Set.of(
                "/admin/operations-dashboard",
                "/admin/analytics", "/admin/analytics/orders", "/admin/analytics/payments",
                "/admin/analytics/settlements", "/admin/analytics/inventory",
                "/admin/commerce/products", "/admin/commerce/categories", "/admin/commerce/options",
                "/admin/commerce/preview", "/admin/commerce/stores", "/admin/commerce/orders",
                "/admin/commerce/suppliers", "/admin/commerce/purchase-orders", "/admin/commerce/receiving", "/admin/commerce/receiving/history",
                "/admin/commerce/inventory", "/admin/commerce/stock-counts", "/admin/commerce/inventory/lots",
                "/admin/commerce/inventory/replenishment", "/admin/commerce/inventory/insights",
                "/admin/commerce/inventory/transfers", "/admin/commerce/shipments", "/admin/commerce/deliveries",
                "/admin/commerce/returns", "/admin/commerce/inventory/transactions",
                "/admin/payment-operations", "/admin/payment-operations/integrated", "/admin/payment-operations/sales-ledger",
                "/admin/payment-operations/pending-sales", "/admin/payment-operations/recovery-tasks",
                "/admin/payment-operations/sales-analytics", "/admin/payment-operations/settlements",
                "/admin/payment-operations/settlements/reconciliation", "/admin/payment-operations/accounting",
                "/admin/pos", "/admin/database-spec", "/admin/navigation", "/admin/audit-logs", "/admin/all-features");
        items.findAllNotDeleted().forEach(i -> {
            if (!alive.contains(i.getItemUrl())) i.softDelete();
        });

        AdminNavigationGroup run     = group("RUN", "운영", 1);
        AdminNavigationGroup settle  = group("SETTLE", "결제 · 정산", 2);
        AdminNavigationGroup shop    = group("SHOP", "커머스", 3);
        AdminNavigationGroup base    = group("BASE", "기준정보", 4);
        AdminNavigationGroup insight = group("INSIGHT", "분석 · 감사", 5);

        // ── 사이드바 최상위 ─────────────────────────────────────────
        feature(run,     "운영 대시보드",   "/admin/operations-dashboard",              "dashboard", 1);
        feature(run,     "전체 기능",       "/admin/all-features",                       "grid",      2);

        feature(settle,  "통합 매출 조회",  "/admin/payment-operations/integrated",      "flow",      1);
        feature(settle,  "결제 예외 처리",  "/admin/payment-operations",                "exception", 2);
        feature(settle,  "정산 마감",       "/admin/payment-operations/settlements",     "settle",    3);
        feature(settle,  "회계 · 분개장",   "/admin/payment-operations/accounting",      "ledger",    4);

        feature(shop,    "재고 · 발주",     "/admin/commerce/inventory",                 "box",       1);
        feature(shop,    "상품 관리",       "/admin/commerce/products",                  "tag",       2);
        feature(shop,    "주문 관리",       "/admin/commerce/orders",                    "cart",      3);

        feature(base,    "매장 관리",       "/admin/commerce/stores",                    "store",     1);
        feature(base,    "공급처 관리",     "/admin/commerce/suppliers",                 "supplier",  2);
        sub(base,        "/admin/commerce/stores", "POS 단말", "/admin/pos",             1);

        feature(insight, "운영 분석",       "/admin/analytics",                          "chart",     1);
        feature(insight, "감사 로그",       "/admin/audit-logs",                         "audit",     2, AdminRole.ADMIN);

        // ── 업무 흐름 하위 단계 (사이드바에서 부모 항목 밑에 펼쳐짐) ──
        sub(settle, "/admin/payment-operations/settlements", "미확정 매출", "/admin/payment-operations/pending-sales",              1);
        sub(settle, "/admin/payment-operations/settlements", "PG 대사",    "/admin/payment-operations/settlements/reconciliation", 2);
        sub(settle, "/admin/payment-operations/settlements", "매출 원장",  "/admin/payment-operations/sales-ledger",               3);

        sub(shop,   "/admin/commerce/inventory", "발주서",    "/admin/commerce/purchase-orders",     1);
        sub(shop,   "/admin/commerce/inventory", "입고 검수", "/admin/commerce/receiving",           2);
        sub(shop,   "/admin/commerce/inventory", "입고 내역", "/admin/commerce/receiving/history",   3);
        sub(shop,   "/admin/commerce/inventory", "재고 실사", "/admin/commerce/stock-counts",        4);
        sub(shop,   "/admin/commerce/inventory", "재고 이동", "/admin/commerce/inventory/transfers", 5);

        sub(shop,   "/admin/commerce/products",  "카테고리",  "/admin/commerce/categories",          1);
        sub(shop,   "/admin/commerce/products",  "옵션",      "/admin/commerce/options",             2);

        sub(shop,   "/admin/commerce/orders",    "출고",      "/admin/commerce/shipments",           1);
        sub(shop,   "/admin/commerce/orders",    "배송",      "/admin/commerce/deliveries",          2);
        sub(shop,   "/admin/commerce/orders",    "반품",      "/admin/commerce/returns",             3);

        // ── 전체 기능에서만 (displayYn=false, 사이드바 미노출) ─────────
        hidden(settle, "복구 작업",      "/admin/payment-operations/recovery-tasks",            12);
        hidden(settle, "매출 분석",      "/admin/payment-operations/sales-analytics",           16);

        hidden(shop, "구매·결제 시뮬레이션", "/admin/commerce/preview",              23);
        hidden(shop, "LOT · 유통기한",   "/admin/commerce/inventory/lots",          29);
        hidden(shop, "발주 제안",        "/admin/commerce/inventory/replenishment", 30);
        hidden(shop, "입출고 내역",      "/admin/commerce/inventory/transactions",  35);
        hidden(shop, "재고 인사이트",    "/admin/commerce/inventory/insights",      36);

        hidden(insight, "주문 분석",     "/admin/analytics/orders",       41);
        hidden(insight, "결제 분석",     "/admin/analytics/payments",     42);
        hidden(insight, "정산 분석",     "/admin/analytics/settlements",  43);
        hidden(insight, "재고 분석",     "/admin/analytics/inventory",    44);
        hidden(insight, "DB 명세",       "/admin/database-spec",          45);
        hiddenAdmin(insight, "메뉴 관리", "/admin/navigation",            46);
    }

    private AdminNavigationGroup group(String code, String name, int order) {
        return groups.findByGroupCode(code)
                .map(g -> { g.update(name, order, true); return g; })
                .orElseGet(() -> groups.save(new AdminNavigationGroup(null, code, name, order, true)));
    }

    private void feature(AdminNavigationGroup g, String name, String url, String icon, int order) {
        feature(g, name, url, icon, order, AdminRole.USER);
    }

    private void feature(AdminNavigationGroup g, String name, String url, String icon, int order, AdminRole role) {
        upsert(g, name, url, icon, order, true, role);
    }

    private void hidden(AdminNavigationGroup g, String name, String url, int order) {
        upsert(g, name, url, "", order, false, AdminRole.USER);
    }

    /** 업무 흐름 하위 단계 — 사이드바에서 부모 항목({@code parentUrl}) 밑에 펼쳐진다.
     *  displayYn=false 라 최상위로는 안 뜨지만 "전체 기능"에는 그대로 나온다. */
    private void sub(AdminNavigationGroup g, String parentUrl, String name, String url, int order) {
        Long parentId = items.findByItemUrlAndIsDeletedFalse(parentUrl).map(AdminNavigationItem::getId).orElse(null);
        items.findByItemUrlAndIsDeletedFalse(url).ifPresentOrElse(
                i -> i.update(g, parentId, name, url, "", 2, order, true, false, AdminRole.USER),
                () -> items.save(AdminNavigationItem.builder()
                        .navigationGroup(g).parentNavigationItemId(parentId).itemName(name).itemUrl(url).icon("")
                        .depth(2).sortOrder(order).useYn(true).displayYn(false).requiredRole(AdminRole.USER).build()));
    }

    private void hiddenAdmin(AdminNavigationGroup g, String name, String url, int order) {
        upsert(g, name, url, "", order, false, AdminRole.ADMIN);
    }

    private void upsert(AdminNavigationGroup g, String name, String url, String icon, int order,
                        boolean display, AdminRole role) {
        items.findByItemUrlAndIsDeletedFalse(url).ifPresentOrElse(
                i -> i.update(g, null, name, url, icon, 1, order, true, display, role),
                () -> items.save(AdminNavigationItem.builder()
                        .navigationGroup(g).itemName(name).itemUrl(url).icon(icon)
                        .depth(1).sortOrder(order).useYn(true).displayYn(display).requiredRole(role).build()));
    }
}
