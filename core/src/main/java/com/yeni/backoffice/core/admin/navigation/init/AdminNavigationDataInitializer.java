package com.yeni.backoffice.core.admin.navigation.init;
import com.yeni.backoffice.core.admin.auth.AdminRole;
import com.yeni.backoffice.core.admin.navigation.entity.*;
import com.yeni.backoffice.core.admin.navigation.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Set;

@Component
public class AdminNavigationDataInitializer implements CommandLineRunner{
 private final AdminNavigationGroupRepository groups;private final AdminNavigationItemRepository items;
 public AdminNavigationDataInitializer(AdminNavigationGroupRepository g,AdminNavigationItemRepository i){groups=g;items=i;}
 @Override @Transactional public void run(String... args){normalize();}
 private void normalize(){
  Set<String> visible=Set.of("/admin/operations-dashboard","/admin/analytics","/admin/analytics/orders","/admin/analytics/payments","/admin/analytics/settlements","/admin/analytics/inventory","/admin/commerce/products","/admin/commerce/categories","/admin/commerce/options","/admin/commerce/preview","/admin/commerce/stores","/admin/commerce/orders","/admin/commerce/suppliers","/admin/commerce/purchase-orders","/admin/commerce/receiving","/admin/commerce/inventory","/admin/commerce/stock-counts","/admin/commerce/inventory/lots","/admin/commerce/inventory/replenishment","/admin/commerce/inventory/insights","/admin/commerce/inventory/transfers","/admin/commerce/shipments","/admin/commerce/deliveries","/admin/commerce/returns","/admin/commerce/inventory/transactions","/admin/payment-operations","/admin/payment-operations/sales-ledger","/admin/payment-operations/pending-sales","/admin/payment-operations/recovery-tasks","/admin/payment-operations/sales-analytics","/admin/payment-operations/settlements","/admin/payment-operations/settlements/reconciliation","/admin/payment-operations/accounting","/admin/database-spec","/admin/navigation","/admin/audit-logs");
  items.findAllNotDeleted().forEach(i->{if(!visible.contains(i.getItemUrl()))i.softDelete();});
  AdminNavigationGroup portfolio=group("PORTFOLIO","포트폴리오",1),analytics=group("ANALYTICS","분석",2),commerce=group("COMMERCE_ADMIN","상품 관리",3),inventory=group("INVENTORY_OPERATION","재고·물류",4),operation=group("PAYMENT_OPERATION","결제·정산",5),admin=group("OPERATION","운영 관리",6);
  ensure(portfolio,"운영 대시보드","/admin/operations-dashboard","⌂",1,AdminRole.USER);
  ensure(analytics,"운영 개요","/admin/analytics","",1,AdminRole.USER);ensure(analytics,"주문 분석","/admin/analytics/orders","",2,AdminRole.USER);ensure(analytics,"결제 분석","/admin/analytics/payments","",3,AdminRole.USER);ensure(analytics,"정산 분석","/admin/analytics/settlements","",4,AdminRole.USER);ensure(analytics,"재고 분석","/admin/analytics/inventory","",5,AdminRole.USER);
  // 고객이 실제로 구매하는 흐름(상품 → 미리보기·결제 시뮬레이션)을 먼저 보여준 뒤,
  // 그 결과로 쌓이는 주문을 관리하는 화면(주문 관리)이 이어지도록 정렬한다.
  ensure(commerce,"상품 목록","/admin/commerce/products","▦",1,AdminRole.USER);ensure(commerce,"카테고리 관리","/admin/commerce/categories","▤",2,AdminRole.USER);ensure(commerce,"옵션 관리","/admin/commerce/options","⌘",3,AdminRole.USER);
  ensure(commerce,"상품 미리보기","/admin/commerce/preview","",4,AdminRole.USER);
  ensure(commerce,"주문 관리","/admin/commerce/orders","▣",5,AdminRole.USER);
  ensure(commerce,"매장 관리","/admin/commerce/stores","",6,AdminRole.USER);
  ensure(inventory,"공급처 관리","/admin/commerce/suppliers","",1,AdminRole.USER);ensure(inventory,"발주 관리","/admin/commerce/purchase-orders","▤",2,AdminRole.USER);ensure(inventory,"입고 처리","/admin/commerce/receiving","▽",3,AdminRole.USER);ensure(inventory,"재고 현황","/admin/commerce/inventory","▥",4,AdminRole.USER);ensure(inventory,"재고 실사","/admin/commerce/stock-counts","▦",5,AdminRole.USER);ensure(inventory,"LOT·유통기한","/admin/commerce/inventory/lots","□",6,AdminRole.USER);ensure(inventory,"발주 제안","/admin/commerce/inventory/replenishment","!",7,AdminRole.USER);ensure(inventory,"재고 이동","/admin/commerce/inventory/transfers","⇄",8,AdminRole.USER);ensure(inventory,"출고 관리","/admin/commerce/shipments","△",9,AdminRole.USER);ensure(inventory,"배송 관리","/admin/commerce/deliveries","▷",10,AdminRole.USER);ensure(inventory,"반품 관리","/admin/commerce/returns","↩",11,AdminRole.USER);ensure(inventory,"입출고 내역","/admin/commerce/inventory/transactions","⇄",12,AdminRole.USER);ensure(inventory,"재고 인사이트","/admin/commerce/inventory/insights","◇",13,AdminRole.USER);
  ensure(operation,"PG 운영","/admin/payment-operations","PG",1,AdminRole.USER);ensure(operation,"복구 작업","/admin/payment-operations/recovery-tasks","↻",2,AdminRole.USER);ensure(operation,"매출 원장","/admin/payment-operations/sales-ledger","₩",3,AdminRole.USER);ensure(operation,"미확정 매출","/admin/payment-operations/pending-sales","⏳",4,AdminRole.USER);ensure(operation,"매출 분석","/admin/payment-operations/sales-analytics","%",5,AdminRole.USER);ensure(operation,"PG 정산 대사","/admin/payment-operations/settlements/reconciliation","✓",6,AdminRole.USER);ensure(operation,"정산 관리","/admin/payment-operations/settlements","✓",7,AdminRole.USER);ensure(operation,"회계 · 분개장","/admin/payment-operations/accounting","₩",8,AdminRole.USER);ensure(operation,"DB 명세","/admin/database-spec","DB",9,AdminRole.USER);
  ensure(admin,"메뉴 관리","/admin/navigation","☰",1,AdminRole.ADMIN);
  ensure(admin,"감사 로그","/admin/audit-logs","",2,AdminRole.ADMIN);
 }
 private AdminNavigationGroup group(String code,String name,int order){return groups.findByGroupCode(code).map(g->{g.update(name,order,true);return g;}).orElseGet(()->groups.save(new AdminNavigationGroup(null,code,name,order,true)));}
 private void ensure(AdminNavigationGroup g,String name,String url,String icon,int order,AdminRole role){String displayName=displayName(url,name);items.findByItemUrlAndIsDeletedFalse(url).ifPresentOrElse(i->i.update(g,null,displayName,url,icon,1,order,true,true,role),()->items.save(AdminNavigationItem.builder().navigationGroup(g).itemName(displayName).itemUrl(url).icon(icon).depth(1).sortOrder(order).useYn(true).displayYn(true).requiredRole(role).build()));}
 private String displayName(String url,String fallback){return switch(url){case "/admin/payment-operations"->"PG 거래";case "/admin/payment-operations/sales-ledger"->"매출 원장";case "/admin/payment-operations/settlements"->"정산 관리";case "/admin/commerce/preview"->"구매·결제 시뮬레이션";default->fallback;};}
}
