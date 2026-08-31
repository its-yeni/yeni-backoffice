package com.yeni.backoffice.api.commerce.rest;import com.yeni.backoffice.core.commerce.dto.ProductOptionDtos.*;import com.yeni.backoffice.core.commerce.service.ProductOptionService;import jakarta.validation.Valid;import org.springframework.http.ResponseEntity;import org.springframework.web.bind.annotation.*;import java.util.*;
@RestController @RequestMapping("/admin/api/commerce") public class ProductOptionRestController{
 private final ProductOptionService service;public ProductOptionRestController(ProductOptionService s){service=s;}
 @GetMapping("/products/{productId}/option-groups")public ResponseEntity<List<GroupResponse>> groups(@PathVariable Long productId){return ResponseEntity.ok(service.groups(productId));}
 @PostMapping("/products/{productId}/option-groups")public ResponseEntity<GroupResponse> createGroup(@PathVariable Long productId,@Valid @RequestBody GroupRequest r){return ResponseEntity.ok(service.createGroup(productId,r));}
 @PostMapping("/products/{productId}/option-groups/from-template/{templateId}")public ResponseEntity<GroupResponse> applyTemplate(@PathVariable Long productId,@PathVariable Long templateId){return ResponseEntity.ok(service.applyTemplate(productId,templateId));}
 @PutMapping("/option-groups/{id}")public ResponseEntity<GroupResponse> updateGroup(@PathVariable Long id,@Valid @RequestBody GroupRequest r){return ResponseEntity.ok(service.updateGroup(id,r));}
 @PostMapping("/option-groups/{id}/values")public ResponseEntity<ValueResponse> createValue(@PathVariable Long id,@Valid @RequestBody ValueRequest r){return ResponseEntity.ok(service.createValue(id,r));}
 @PutMapping("/option-values/{id}")public ResponseEntity<ValueResponse> updateValue(@PathVariable Long id,@Valid @RequestBody ValueRequest r){return ResponseEntity.ok(service.updateValue(id,r));}
 @PutMapping("/products/{productId}/option-groups/order")public ResponseEntity<Void> reorderGroups(@PathVariable Long productId,@Valid @RequestBody ReorderRequest r){service.reorderGroups(productId,r);return ResponseEntity.noContent().build();}
 @PutMapping("/option-groups/{id}/values/order")public ResponseEntity<Void> reorderValues(@PathVariable Long id,@Valid @RequestBody ReorderRequest r){service.reorderValues(id,r);return ResponseEntity.noContent().build();}
 @GetMapping("/products/{productId}/addon-groups")public ResponseEntity<List<AddonGroupResponse>> addons(@PathVariable Long productId){return ResponseEntity.ok(service.addonGroups(productId));}
 @PostMapping("/products/{productId}/addon-groups")public ResponseEntity<AddonGroupResponse> createAddonGroup(@PathVariable Long productId,@Valid @RequestBody AddonGroupRequest r){return ResponseEntity.ok(service.createAddonGroup(productId,r));}
 @PostMapping("/addon-groups/{id}/items")public ResponseEntity<AddonGroupResponse> addAddon(@PathVariable Long id,@Valid @RequestBody AddonItemRequest r){return ResponseEntity.ok(service.addAddon(id,r));}
 @DeleteMapping("/addon-groups/{id}/items/{productId}")public ResponseEntity<Void> remove(@PathVariable Long id,@PathVariable Long productId){service.removeAddon(id,productId);return ResponseEntity.noContent().build();}
}
