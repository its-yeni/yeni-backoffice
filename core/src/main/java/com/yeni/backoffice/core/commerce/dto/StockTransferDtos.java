package com.yeni.backoffice.core.commerce.dto;
import com.yeni.backoffice.core.commerce.entity.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
public final class StockTransferDtos { private StockTransferDtos(){}
 public record TransferItemRequest(@NotNull Long variantId,@Positive int quantity){}
 public record TransferCreateRequest(@NotNull Long sourceStoreId,@NotNull Long destinationStoreId,@NotEmpty @Valid List<TransferItemRequest> items,String reason){}
 public record TransferItemResponse(Long variantId,String productName,String sku,String optionSummary,int quantity,BigDecimal unitCost){}
 public record TransferResponse(Long id,String transferNo,Long sourceStoreId,String sourceStoreName,Long destinationStoreId,String destinationStoreName,String status,String reason,LocalDateTime shippedAt,LocalDateTime receivedAt,List<TransferItemResponse> items,LocalDateTime createdAt){}
}
