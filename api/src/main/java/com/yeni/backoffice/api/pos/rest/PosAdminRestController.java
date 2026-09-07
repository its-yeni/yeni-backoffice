package com.yeni.backoffice.api.pos.rest;
import com.yeni.backoffice.core.pos.service.PosTerminalAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.yeni.backoffice.core.pos.entity.PosTerminal;
import com.yeni.backoffice.core.pos.service.PosSyncManagementService;
import com.yeni.backoffice.core.pos.service.PosClientLogService;

@RestController @RequestMapping("/admin/api/pos/terminals")
public class PosAdminRestController {
    private final PosTerminalAdminService service;private final PosSyncManagementService sync;private final PosClientLogService logs;
    public PosAdminRestController(PosTerminalAdminService service,PosSyncManagementService sync,PosClientLogService logs){this.service=service;this.sync=sync;this.logs=logs;}
    @GetMapping public ResponseEntity<List<PosTerminalAdminService.TerminalView>> list(@RequestParam(required=false) Long storeId){return ResponseEntity.ok(service.list(storeId));}
    @PostMapping public ResponseEntity<PosTerminalAdminService.ProvisionedTerminal> create(@RequestBody PosTerminalAdminService.CreateTerminal request){return ResponseEntity.ok(service.create(request));}
    @PutMapping("/{terminalId}") public ResponseEntity<PosTerminalAdminService.TerminalView> update(@PathVariable Long terminalId,@RequestBody PosTerminalAdminService.UpdateTerminal request){return ResponseEntity.ok(service.update(terminalId,request));}
    @PostMapping("/{terminalId}/credential/rotate") public ResponseEntity<PosTerminalAdminService.ProvisionedTerminal> rotate(@PathVariable Long terminalId){return ResponseEntity.ok(service.rotateCredential(terminalId));}
    @GetMapping("/{terminalId}/sync") public ResponseEntity<PosSyncManagementService.SyncOverview> sync(@PathVariable Long terminalId){return ResponseEntity.ok(sync.overview(service.get(terminalId)));}
    @PostMapping("/{terminalId}/sync/{executionId}/retry") public ResponseEntity<PosSyncManagementService.SyncExecution> retry(@PathVariable Long terminalId,@PathVariable Long executionId){return ResponseEntity.ok(sync.requestRetry(service.get(terminalId),executionId));}
    @GetMapping("/{terminalId}/logs") public ResponseEntity<List<PosClientLogService.LogView>> logs(@PathVariable Long terminalId,@RequestParam(required=false) Integer limit){return ResponseEntity.ok(logs.list(service.get(terminalId),limit));}
    @PostMapping("/{terminalId}/logs/{logId}/retried") public ResponseEntity<PosClientLogService.LogView> retried(@PathVariable Long terminalId,@PathVariable Long logId){return ResponseEntity.ok(logs.markRetried(service.get(terminalId),logId));}
}
