package com.yeni.backoffice.api.admin.audit.view;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AdminAuditViewController {
    @GetMapping("/admin/audit-logs")
    public String page() { return "admin/audit-logs"; }
}
