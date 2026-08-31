package com.yeni.backoffice.core.commerce.service;
import com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.*;
import com.yeni.backoffice.core.commerce.entity.*;
import com.yeni.backoffice.core.commerce.enums.*;
import com.yeni.backoffice.core.commerce.repository.*;
import com.yeni.backoffice.core.common.exception.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Service
public class ProductVariantService{
 private final ProductRepository products;private final ProductOptionGroupRepository groups;private final ProductOptionValueRepository values;private final ProductVariantRepository variants;private final InventoryTransactionService inventoryTransactions;private final StoreVariantInventoryRepository storeInventories;private final CommerceStoreRepository stores;private final InventoryLotRepository lots;private final InventoryLedgerService ledger;
 public ProductVariantService(ProductRepository p,ProductOptionGroupRepository g,ProductOptionValueRepository v,ProductVariantRepository vr,InventoryTransactionService it,StoreVariantInventoryRepository si,CommerceStoreRepository s,InventoryLotRepository l,InventoryLedgerService ledger){products=p;groups=g;values=v;variants=vr;inventoryTransactions=it;storeInventories=si;stores=s;lots=l;this.ledger=ledger;}
 @Transactional(readOnly=true)public List<VariantResponse> list(Long productId){product(productId);return variants.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().map(VariantResponse::from).toList();}
 @Transactional(readOnly=true)public List<com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantInventoryResponse> listAllInventory(){
  return listAllInventory(null);
 }
 @Transactional(readOnly=true)public List<com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantInventoryResponse> listAllInventory(String storeCode){
  List<ProductVariant> all=variants.findAllByOrderByProductIdAscSortOrderAscIdAsc();
  Map<Long,Product> productMap=products.findAllById(all.stream().map(ProductVariant::getProductId).distinct().toList()).stream().collect(java.util.stream.Collectors.toMap(Product::getId,p->p));
  return all.stream().filter(v->{Product p=productMap.get(v.getProductId());return storeCode==null||storeCode.isBlank()||(p!=null&&storeCode.equals(p.getStoreCode()));}).map(v->{Product p=productMap.get(v.getProductId());return new com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantInventoryResponse(v.getId(),v.getProductId(),p==null?"-":p.getProductName(),p==null?"-":p.getProductCode(),p==null?null:p.getCategory(),v.getSku(),v.getBarcode(),v.getOptionSummary(),v.getStockQuantity(),v.getReservedQuantity(),v.getAvailableQuantity(),v.getSafetyStock(),v.getSaleStatus().name(),v.getUpdatedAt());}).toList();
 }
 @Transactional(readOnly=true)public VariantResponse findByBarcode(String barcode){ProductVariant v=variants.findByBarcode(barcode).orElseThrow(()->new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND,"해당 바코드로 등록된 SKU가 없습니다."));return VariantResponse.from(v);}
 /** 직접 입고(발주 없는 입고 — 초도 등록·수기 조정 입고). 발주 기반 입고는 PurchaseOrderService.receive 를 쓴다. */
 @Transactional public VariantResponse receive(Long id,com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantReceiveRequest r){
  ProductVariant v=variants.findByIdForUpdate(id).orElseThrow(()->new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
  Long storeId=resolveStore(v,r.storeId());
  if(storeId!=null){
   ledger.receiveWithLot(storeId,id,r.quantity(),r.unitCost(),r.lotNo(),r.manufacturedDate(),r.expirationDate(),r.memo(),
     r.reason()==null||r.reason().isBlank()?"직접 입고":r.reason(),"MANUAL",null,"ADMIN");
  }else{
   v.receiveStock(r.quantity());
   inventoryTransactions.record(v,com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.RECEIPT,r.quantity(),r.reason(),"MANUAL",null,"ADMIN");
  }
  syncProductStock(v.getProductId());
  return VariantResponse.from(variants.findById(id).orElseThrow());
 }
 private Long resolveStore(ProductVariant v,Long explicit){
  if(explicit!=null)return explicit;
  Product p=product(v.getProductId());
  return stores.findByStoreCode(p.getStoreCode()).map(CommerceStore::getId).orElse(null);
 }
 @Transactional public VariantResponse adjust(Long id,com.yeni.backoffice.core.commerce.dto.ProductVariantDtos.VariantAdjustRequest r){
  ProductVariant v=variants.findByIdForUpdate(id).orElseThrow(()->new NotFoundException(ErrorCode.PRODUCT_VARIANT_NOT_FOUND));
  int delta=r.delta();if(delta==0)throw validation("조정 수량은 0이 될 수 없습니다.");
  Long storeId=resolveStore(v,null);
  if(storeId!=null){
   ledger.adjust(storeId,id,delta,r.reason()==null||r.reason().isBlank()?"수기 조정":r.reason(),"MANUAL",null,"ADMIN");
  }else{
   v.adjust(delta);
   inventoryTransactions.record(v,delta>0?com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.ADJUST_IN:com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.ADJUST_OUT,Math.abs(delta),r.reason(),"MANUAL",null,"ADMIN");
  }
  syncProductStock(v.getProductId());
  return VariantResponse.from(variants.findById(id).orElseThrow());
 }
 @Transactional public List<VariantResponse> generate(Long productId){Product product=product(productId);List<ProductOptionGroup> target=groups.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().filter(g->g.isExposed()&&g.getSelectionType()==OptionSelectionType.SINGLE).toList();if(target.isEmpty())throw validation("조합을 만들 단일 선택 옵션 그룹이 없습니다.");List<List<ProductOptionValue>> dimensions=target.stream().map(g->values.findByOptionGroupIdOrderBySortOrderAscIdAsc(g.getId()).stream().filter(v->v.getSaleStatus()!=ProductSaleStatus.STOPPED).toList()).toList();if(dimensions.stream().anyMatch(List::isEmpty))throw validation("옵션 항목이 없는 그룹이 있습니다.");long count=dimensions.stream().mapToLong(List::size).reduce(1,(a,b)->a*b);if(count>500)throw validation("옵션 조합은 한 번에 500개까지 생성할 수 있습니다.");List<List<ProductOptionValue>> combinations=new ArrayList<>();combine(dimensions,0,new ArrayList<>(),combinations);int order=variants.findByProductIdOrderBySortOrderAscIdAsc(productId).size();for(List<ProductOptionValue> combination:combinations){String key=combination.stream().map(v->v.getId().toString()).reduce((a,b)->a+","+b).orElse("");if(variants.findByProductIdAndCombinationKey(productId,key).isPresent())continue;String summary=combination.stream().map(ProductOptionValue::getValueName).reduce((a,b)->a+" / "+b).orElse("");BigDecimal price=combination.stream().map(ProductOptionValue::getAdditionalPrice).reduce(BigDecimal.ZERO,BigDecimal::add);String sku=uniqueSku(product.getProductCode()+"-"+combination.stream().map(v->slug(v.getValueName())).reduce((a,b)->a+"-"+b).orElse("VAR"));variants.save(ProductVariant.builder().productId(productId).sku(sku).combinationKey(key).optionSummary(summary).additionalPrice(price).stockQuantity(0).safetyStock(ProductVariant.DEFAULT_SAFETY_STOCK).saleStatus(ProductSaleStatus.SOLD_OUT).sortOrder(++order).build());}return list(productId);}
 @Transactional public VariantResponse update(Long id,VariantUpdateRequest r){ProductVariant v=variants.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.NOT_FOUND));ProductSaleStatus status;try{status=ProductSaleStatus.valueOf(r.saleStatus());}catch(Exception e){throw validation("판매 상태를 확인해 주세요.");}String barcode=r.barcode()==null||r.barcode().isBlank()?null:r.barcode().trim();if(barcode!=null&&variants.existsByBarcodeAndIdNot(barcode,id))throw new com.yeni.backoffice.core.common.exception.ConflictException(ErrorCode.PRODUCT_VARIANT_BARCODE_DUPLICATED);
  int before=v.getStockQuantity();
  Long storeId=resolveStore(v,null);
  if(storeId!=null){
   // 재고는 원장을 통해서만 바꾼다 — 나머지 필드만 반영하고 수량 델타는 조정으로 처리.
   v.update(r.sku(),barcode,r.additionalPrice(),before,status);
   int delta=r.stockQuantity()-before;
   if(delta!=0)ledger.adjust(storeId,id,delta,"SKU 직접 수정","MANUAL",null,"ADMIN");
  }else{
   v.update(r.sku(),barcode,r.additionalPrice(),r.stockQuantity(),status);
   int delta=v.getStockQuantity()-before;
   if(delta!=0)inventoryTransactions.record(v,delta>0?com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.ADJUST_IN:com.yeni.backoffice.core.commerce.enums.InventoryTransactionType.ADJUST_OUT,Math.abs(delta),"SKU 직접 수정","MANUAL",null,"ADMIN");
  }
  syncProductStock(v.getProductId());
  return VariantResponse.from(variants.findById(id).orElseThrow());}
 private void syncProductStock(Long productId){Product p=product(productId);int stock=variants.findByProductIdOrderBySortOrderAscIdAsc(productId).stream().mapToInt(ProductVariant::getStockQuantity).sum();p.applyVariantStock(stock);products.save(p);}
 private void combine(List<List<ProductOptionValue>> d,int index,List<ProductOptionValue> current,List<List<ProductOptionValue>> out){if(index==d.size()){out.add(List.copyOf(current));return;}for(ProductOptionValue v:d.get(index)){current.add(v);combine(d,index+1,current,out);current.remove(current.size()-1);}}
 private String uniqueSku(String base){String sku=base.substring(0,Math.min(90,base.length()));int n=1;while(variants.existsBySku(sku)){sku=base.substring(0,Math.min(84,base.length()))+"-"+(n++);}return sku;}
 private String slug(String value){String s=value.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9가-힣]+","");return s.isBlank()?"OPT":s.substring(0,Math.min(12,s.length()));}
 private Product product(Long id){return products.findById(id).orElseThrow(()->new NotFoundException(ErrorCode.PRODUCT_NOT_FOUND));}
 private ValidationBusinessException validation(String m){return new ValidationBusinessException(ErrorCode.VALIDATION_ERROR,m);}
}
