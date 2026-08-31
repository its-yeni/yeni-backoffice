package com.yeni.backoffice.api.commerce.rest;

import com.yeni.backoffice.core.commerce.dto.InventoryPlanningDtos.*;
import com.yeni.backoffice.core.commerce.service.InventoryPlanningService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/admin/api/commerce/inventory-planning")
public class InventoryPlanningRestController {
    private final InventoryPlanningService service;
    public InventoryPlanningRestController(InventoryPlanningService service){this.service=service;}
    @GetMapping("/lots") public ResponseEntity<List<LotResponse>> lots(){return ResponseEntity.ok(service.lots());}
    @GetMapping("/replenishments") public ResponseEntity<List<ReplenishmentResponse>> replenishments(@RequestParam(required=false) Integer leadTimeDays){return ResponseEntity.ok(service.replenishments(leadTimeDays));}
    @GetMapping("/summary") public ResponseEntity<InsightSummary> summary(){return ResponseEntity.ok(service.summary());}
}
