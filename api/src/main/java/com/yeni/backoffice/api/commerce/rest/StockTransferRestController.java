package com.yeni.backoffice.api.commerce.rest;
import com.yeni.backoffice.core.commerce.dto.StockTransferDtos.*;
import com.yeni.backoffice.core.commerce.service.StockTransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/admin/api/commerce/stock-transfers")
public class StockTransferRestController {private final StockTransferService service;public StockTransferRestController(StockTransferService s){service=s;}@GetMapping public ResponseEntity<List<TransferResponse>> list(){return ResponseEntity.ok(service.list());}@PostMapping public ResponseEntity<TransferResponse> create(@Valid @RequestBody TransferCreateRequest r){return ResponseEntity.ok(service.create(r));}@PostMapping("/{id}/ship") public ResponseEntity<TransferResponse> ship(@PathVariable Long id){return ResponseEntity.ok(service.ship(id));}@PostMapping("/{id}/receive") public ResponseEntity<TransferResponse> receive(@PathVariable Long id){return ResponseEntity.ok(service.receive(id));}}
