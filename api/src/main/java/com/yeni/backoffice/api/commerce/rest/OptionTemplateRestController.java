package com.yeni.backoffice.api.commerce.rest;import com.yeni.backoffice.core.commerce.dto.OptionTemplateDtos.*;import com.yeni.backoffice.core.commerce.service.OptionTemplateService;import jakarta.validation.Valid;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/admin/api/commerce/option-templates") public class OptionTemplateRestController{
 private final OptionTemplateService service;public OptionTemplateRestController(OptionTemplateService s){service=s;}
 @GetMapping public ResponseEntity<List<TemplateGroupResponse>> list(){return ResponseEntity.ok(service.list());}
 @PostMapping public ResponseEntity<TemplateGroupResponse> create(@Valid @RequestBody TemplateGroupRequest r){return ResponseEntity.ok(service.create(r));}
 @PutMapping("/{id}") public ResponseEntity<TemplateGroupResponse> update(@PathVariable Long id,@Valid @RequestBody TemplateGroupRequest r){return ResponseEntity.ok(service.update(id,r));}
 @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable Long id){service.delete(id);return ResponseEntity.noContent().build();}
 @PostMapping("/{id}/values") public ResponseEntity<TemplateValueResponse> addValue(@PathVariable Long id,@Valid @RequestBody TemplateValueRequest r){return ResponseEntity.ok(service.createValue(id,r));}
 @PutMapping("/values/{id}") public ResponseEntity<TemplateValueResponse> updateValue(@PathVariable Long id,@Valid @RequestBody TemplateValueRequest r){return ResponseEntity.ok(service.updateValue(id,r));}
 @DeleteMapping("/values/{id}") public ResponseEntity<Void> deleteValue(@PathVariable Long id){service.deleteValue(id);return ResponseEntity.noContent().build();}
}
