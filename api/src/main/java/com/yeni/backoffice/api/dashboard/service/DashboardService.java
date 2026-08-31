package com.yeni.backoffice.api.dashboard.service;

import com.yeni.backoffice.api.dashboard.dto.CareerProjectDto;
import com.yeni.backoffice.api.dashboard.dto.DashboardStatDto;
import com.yeni.backoffice.api.dashboard.dto.DashboardView;
import com.yeni.backoffice.api.dashboard.dto.PlannedFeatureDto;
import com.yeni.backoffice.api.dashboard.dto.RecentOrderDto;
import com.yeni.backoffice.api.dashboard.dto.ResumeSummaryDto;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class DashboardService {

    public DashboardView getDashboardData() {
        ResumeSummaryDto resumeSummary = new ResumeSummaryDto(
                "Ye Eun Kwon",
                "Backoffice / API Developer",
                "C#, .NET, MS-SQL, Dapper",
                "Java 17, Spring Boot 3, JPA",
                "Backoffice, API, POS/KIOSK, Commerce"
        );

        List<DashboardStatDto> stats = Arrays.asList(
                new DashboardStatDto("Today Orders", "128", "Up 12% from yesterday", false),
                new DashboardStatDto("Paid Orders", "104", "Approval rate 81.2%", false),
                new DashboardStatDto("Ready To Ship", "37", "Tracking number required", true),
                new DashboardStatDto("Cancel Requests", "6", "Operator review required", true)
        );

        List<RecentOrderDto> recentOrders = Arrays.asList(
                new RecentOrderDto("ORD-20260602-001", "Kim Yejin", "Basic half sleeve shirt", "29,000 KRW", "Paid", "paid", "2026-06-02 10:21"),
                new RecentOrderDto("ORD-20260602-002", "Lee Minsoo", "Daily eco bag", "18,500 KRW", "Shipping", "shipping", "2026-06-02 10:08"),
                new RecentOrderDto("ORD-20260602-003", "Park Jisoo", "Slim denim pants", "49,000 KRW", "Ready", "ready", "2026-06-02 09:45"),
                new RecentOrderDto("ORD-20260602-004", "Choi Doyeon", "Oversized hoodie", "39,900 KRW", "Cancel Requested", "cancel", "2026-06-02 09:12")
        );

        List<CareerProjectDto> careerProjects = Arrays.asList(
                new CareerProjectDto(
                        "POS/Kiosk Admin",
                        "C# / .NET",
                        "Admin features for POS and kiosk operations, including order, option, promotion, and store settings."
                ),
                new CareerProjectDto(
                        "Payment & Sales API",
                        "API / MSSQL",
                        "Payment status, cancellation, sales settlement, and external API integration experience reflected in this portfolio."
                ),
                new CareerProjectDto(
                        "AlimTalk Agent",
                        "Queue / Retry",
                        "Notification sending, failed retry history, and operation monitoring concepts are planned as admin features."
                )
        );

        List<PlannedFeatureDto> plannedFeatures = Arrays.asList(
                new PlannedFeatureDto("PR", "Product Management", "Create and update products, sale status, and stock quantities."),
                new PlannedFeatureDto("OR", "Order Management", "Search orders, inspect details, and track order status history."),
                new PlannedFeatureDto("PA", "Payment Management", "Mock payment approval, cancellation, and transaction rollback scenarios."),
                new PlannedFeatureDto("DL", "Delivery Management", "Register tracking numbers and manage shipping status history."),
                new PlannedFeatureDto("PM", "Promotion Management", "Manage period-based promotions, discounts, and active status."),
                new PlannedFeatureDto("NQ", "Notification Queue", "Track notification events and retry failed sends.")
        );

        return new DashboardView(resumeSummary, stats, recentOrders, careerProjects, plannedFeatures);
    }
}
