package com.yeni.backoffice.api.dashboard.view;

import com.yeni.backoffice.api.dashboard.service.OperationsDashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class OperationsDashboardViewController {
    private final OperationsDashboardService dashboardService;

    public OperationsDashboardViewController(OperationsDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin/operations-dashboard")
    public String dashboard(Model model, @RequestParam(required = false) Integer days,
            @RequestParam(required = false) Long storeId) {
        model.addAttribute("operations", dashboardService.getDashboard(days, storeId));
        return "dashboard/operations";
    }
}
