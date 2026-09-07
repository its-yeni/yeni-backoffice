package com.yeni.backoffice.api.pos.rest;
import com.yeni.backoffice.core.pos.service.PosTerminalAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/admin/api/pos/terminals")
public class PosAdminRestController {
    private final PosTerminalAdminService service;public PosAdminRestController(PosTerminalAdminService service){this.service=service;}
    @GetMapping public ResponseEntity<List<PosTerminalAdminService.TerminalView>> list(@RequestParam(required=false) Long storeId){return ResponseEntity.ok(service.list(storeId));}
    @PostMapping public ResponseEntity<PosTerminalAdminService.ProvisionedTerminal> create(@RequestBody PosTerminalAdminService.CreateTerminal request){return ResponseEntity.ok(service.create(request));}
    @PutMapping("/{terminalId}") public ResponseEntity<PosTerminalAdminService.TerminalView> update(@PathVariable Long terminalId,@RequestBody PosTerminalAdminService.UpdateTerminal request){return ResponseEntity.ok(service.update(terminalId,request));}
    @PostMapping("/{terminalId}/credential/rotate") public ResponseEntity<PosTerminalAdminService.ProvisionedTerminal> rotate(@PathVariable Long terminalId){return ResponseEntity.ok(service.rotateCredential(terminalId));}
}
