package com.yeni.backoffice.api.pos.view;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
@Controller
public class PosManagementViewController {
    @GetMapping("/admin/pos") public String index(){return "pos/index";}
}
