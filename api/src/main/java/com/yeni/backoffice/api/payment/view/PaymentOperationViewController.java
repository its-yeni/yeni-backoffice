package com.yeni.backoffice.api.payment.view;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/payment-operations")
public class PaymentOperationViewController {

    @GetMapping
    public String page(Model model) {
        model.addAttribute("title", "PG 운영 콘솔");
        model.addAttribute("description", "Mock PG 승인/취소와 매출 원장, 외부전송, 알림톡, 복구 작업 흐름을 확인합니다.");
        return "payment/operations";
    }

    @GetMapping("/integrated")
    public String integrated(Model model) {
        model.addAttribute("title", "통합 매출 조회");
        model.addAttribute("description", "주문 1건의 결제·배송·매출원장·구매확정·대사·정산·지급을 한 화면에서 추적합니다.");
        return "payment/integrated-sales";
    }

    @GetMapping("/sales-ledger")
    public String salesLedger(Model model) {
        model.addAttribute("title", "매출 원장");
        model.addAttribute("description", "결제 승인과 취소 결과가 확정된 SALE/CANCEL 거래를 조회합니다.");
        return "payment/sales-ledger";
    }

    @GetMapping("/sales-analytics")
    public String salesAnalytics(Model model) {
        model.addAttribute("title", "매출 분석");
        model.addAttribute("description", "분류별·상품별 매출 집계와 주문→매출→정산 금액 정합성 검산을 확인합니다.");
        return "payment/sales-analytics";
    }

    @GetMapping("/pending-sales")
    public String pendingSales(Model model) {
        model.addAttribute("title", "미확정 매출");
        model.addAttribute("description", "배송 완료(구매 확정) 전이라 아직 정산 대상이 아닌 매출을 조회합니다.");
        return "payment/pending-sales";
    }

    @GetMapping("/recovery-tasks")
    public String recoveryTasks(Model model) {
        model.addAttribute("title", "복구 작업");
        model.addAttribute("description", "결과불명·후속 처리 실패로 남은 복구 작업을 조회하고 재시도·성공/실패 처리합니다.");
        return "payment/recovery-tasks";
    }

    @GetMapping("/accounting")
    public String accounting(Model model) {
        model.addAttribute("title", "회계 · 분개장");
        model.addAttribute("description", "매출 원장과 정산 명세를 복식부기 분개로 전기하고 시산표·손익계산서로 집계합니다.");
        return "payment/accounting";
    }

    @GetMapping("/settlements")
    public String settlements(Model model) {
        model.addAttribute("title", "정산 관리");
        model.addAttribute("description", "매출 원장을 기준으로 정산 예정금액과 정산 상태를 확인합니다.");
        return "payment/settlements";
    }

    @GetMapping({"/settlements/reconciliation", "/pg-reconciliation"})
    public String reconciliation() { return "payment/pg-reconciliation"; }
}
