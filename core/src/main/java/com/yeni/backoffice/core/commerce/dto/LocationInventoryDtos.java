package com.yeni.backoffice.core.commerce.dto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
public final class LocationInventoryDtos { private LocationInventoryDtos(){}
 public record LocationInventoryResponse(Long inventoryId,Long storeId,String storeCode,String storeName,Long variantId,Long productId,String productName,String productCode,String category,String sku,String optionSummary,int stockQuantity,int reservedQuantity,int availableQuantity,int inTransitQuantity,int onOrderQuantity,int lotAvailableTotal,int safetyStock,BigDecimal averageUnitCost,BigDecimal inventoryAssetValue,boolean saleEnabled,String saleStatus,LocalDateTime updatedAt){
  /** 입고 예정 = 이동 중 + 발주 미입고. */
  public int incomingQuantity(){return inTransitQuantity+onOrderQuantity;}
  /** LOT 잔량 합이 매장 현재재고와 어긋나면 true(정합성 경고용). */
  public boolean lotMismatch(){return lotAvailableTotal!=stockQuantity;}
 }
}
