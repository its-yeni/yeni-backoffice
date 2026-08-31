package com.yeni.backoffice.api.admin.audit.rest;

import com.yeni.backoffice.api.admin.audit.dto.AdminAuditLogResponse;
import com.yeni.backoffice.api.admin.audit.service.AdminAuditQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/api/audit-logs")
public class AdminAuditRestController {
    private final AdminAuditQueryService service;
    public AdminAuditRestController(AdminAuditQueryService service) { this.service = service; }

    @GetMapping
    public ResponseEntity<List<AdminAuditLogResponse>> list() {
        return ResponseEntity.ok(service.recent());
    }
}
