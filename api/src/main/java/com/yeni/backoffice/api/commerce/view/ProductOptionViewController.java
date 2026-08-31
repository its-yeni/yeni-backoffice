package com.yeni.backoffice.api.commerce.view;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller public class ProductOptionViewController{
 @GetMapping("/admin/commerce/product-options")public String page(){return "commerce/product-options";}
 // 옵션 편집은 상품 단위 작업이라 어떤 상품을 편집할지 먼저 고르게 한다.
 // 예전엔 findFirst()로 임의 상품에 바로 리다이렉트했는데, 왜 그 상품이 열렸는지 알 수 없는 문제가 있었다.
 @GetMapping("/admin/commerce/options")public String options(){return "commerce/option-picker";}
}
