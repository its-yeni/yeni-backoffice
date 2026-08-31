package com.yeni.backoffice.api.dashboard.view;

import com.yeni.backoffice.api.dashboard.service.DashboardService;
import com.yeni.backoffice.api.dashboard.service.PortfolioContentService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardViewController {

    private final DashboardService dashboardService;
    private final PortfolioContentService portfolioContentService;

    public DashboardViewController(
            DashboardService dashboardService,
            PortfolioContentService portfolioContentService) {
        this.dashboardService = dashboardService;
        this.portfolioContentService = portfolioContentService;
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/admin/operations-dashboard";
    }

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.getDashboardData());
        model.addAttribute("portfolioProjects", portfolioContentService.getProjects());
        return "dashboard/index";
    }
}

