package com.yeni.backoffice.api.commerce.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class ProductViewController {
    @GetMapping("/admin/commerce/products")
    public String products(Model model) {
        model.addAttribute("title", "상품 관리");
        model.addAttribute("description", "주문 금액과 재고의 기준이 되는 상품 원장을 관리합니다.");
        return "commerce/products";
    }

    @GetMapping("/admin/commerce/preview")
    public String preview() {
        return "commerce/product-preview-page";
    }

    @GetMapping("/admin/commerce/stores")
    public String stores() {
        return "commerce/stores";
    }

    @GetMapping("/admin/commerce/inventory")
    public String inventory(Model model) {
        model.addAttribute("title", "재고 현황");
        model.addAttribute("description", "SKU별 현재 재고와 바코드를 확인합니다.");
        return "commerce/inventory";
    }

    @GetMapping("/admin/commerce/inventory/lots")
    public String inventoryLots(Model model) { model.addAttribute("planningMode", "lots"); return "commerce/inventory-planning"; }

    @GetMapping("/admin/commerce/inventory/replenishment")
    public String inventoryReplenishment(Model model) { model.addAttribute("planningMode", "replenishment"); return "commerce/inventory-planning"; }

    @GetMapping("/admin/commerce/inventory/insights")
    public String inventoryInsights(Model model) { model.addAttribute("planningMode", "insights"); return "commerce/inventory-planning"; }

    @GetMapping("/admin/commerce/inventory/transactions")
    public String inventoryTransactions(Model model) {
        model.addAttribute("title", "입출고 내역");
        model.addAttribute("description", "입고·판매·복구 등 SKU 재고 변동 이력을 확인합니다.");
        return "commerce/inventory-transactions";
    }

    @GetMapping("/admin/commerce/inventory/transfers")
    public String inventoryTransfers() { return "commerce/inventory-transfers"; }

    @GetMapping("/admin/commerce/receiving")
    public String receiving(Model model) {
        model.addAttribute("title", "입고 검수");
        model.addAttribute("description", "SKU를 검색해 입고 수량을 등록합니다.");
        return "commerce/receiving";
    }

    @GetMapping("/admin/commerce/receiving/history")
    public String receivingHistory(Model model) {
        model.addAttribute("title", "입고 내역");
        model.addAttribute("description", "등록된 입고 이력을 조회합니다.");
        return "commerce/receiving-history";
    }

    @GetMapping("/admin/commerce/shipments")
    public String shipments(Model model) {
        model.addAttribute("title", "출고 관리");
        model.addAttribute("description", "결제 완료된 주문의 출고를 처리합니다.");
        return "commerce/shipments";
    }

    @GetMapping("/admin/commerce/deliveries")
    public String deliveries(Model model) {
        model.addAttribute("title", "배송 관리");
        model.addAttribute("description", "주문의 배송지 정보와 배송 준비·배송 중·배송 완료 상태를 관리합니다.");
        return "commerce/deliveries";
    }

    @GetMapping("/admin/commerce/returns")
    public String returns(Model model) {
        model.addAttribute("title", "반품 관리");
        model.addAttribute("description", "반품 접수부터 검수 완료(재고 복원·환불 확정)까지 처리합니다.");
        return "commerce/returns";
    }

    @GetMapping("/admin/commerce/suppliers")
    public String suppliers(Model model) {
        model.addAttribute("title", "공급처 관리");
        model.addAttribute("description", "발주를 넣는 거래처와 리드타임을 관리합니다.");
        return "commerce/suppliers";
    }

    @GetMapping("/admin/commerce/purchase-orders")
    public String purchaseOrders(Model model) {
        model.addAttribute("title", "발주 관리");
        model.addAttribute("description", "공급처에 발주를 넣고 입고(검수)까지 진행합니다.");
        return "commerce/purchase-orders";
    }

    @GetMapping("/admin/commerce/stock-counts")
    public String stockCounts(Model model) {
        model.addAttribute("title", "재고 실사");
        model.addAttribute("description", "매장 재고를 실사해 시스템 수량과의 차이를 조정합니다.");
        return "commerce/stock-counts";
    }
}
