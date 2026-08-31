package com.yeni.backoffice.core.commerce.init;

import com.yeni.backoffice.core.commerce.entity.Supplier;
import com.yeni.backoffice.core.commerce.repository.SupplierRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 데모 공급처(거래처). 발주 시드({@link DemoPurchaseOrderSeedInitializer})가 이 목록을 사용한다.
 * 이름·담당자·연락처는 실제 회사와 무관한 가상 정보다.
 */
@Component
@Profile("fly | demo")
@Order(170)
public class DemoSupplierSeedInitializer implements CommandLineRunner {

    private final SupplierRepository suppliers;

    public DemoSupplierSeedInitializer(SupplierRepository suppliers) { this.suppliers = suppliers; }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DemoSupplierSeedInitializer.class);

    @Override
    public void run(String... args) {
        try { seed(); } catch (Exception e) { log.warn("공급처 시드 스킵", e); }
    }

    private void seed() {
        if (suppliers.count() > 0) return;
        // 실제 회사명과 겹치지 않도록 만든 가상 거래처. 연락처는 문서화용 placeholder(000).
        List<String[]> rows = List.of(
                new String[]{"SUP-001", "예니 OEM 파트너-1", "담당 A", "000-0000-0001", "10", "우븐·니트 완제품 OEM (가상)"},
                new String[]{"SUP-002", "예니 봉제공방-2", "담당 B", "000-0000-0002", "14", "샘플·본생산 봉제 (가상)"},
                new String[]{"SUP-003", "예니 국내도매-3", "담당 C", "000-0000-0003", "5", "국내 도매·즉납 재고 (가상)"},
                new String[]{"SUP-004", "예니 부자재-4", "담당 D", "000-0000-0004", "7", "라벨·지퍼·단추 등 부자재 (가상)"},
                new String[]{"SUP-005", "예니 수입소싱-5", "담당 E", "000-0000-0005", "21", "수입 완제품 대행·해상 (가상)"},
                new String[]{"SUP-006", "예니 니트전문-6", "담당 F", "000-0000-0006", "12", "스웨터·가디건 전문 (가상)"});
        for (String[] r : rows) {
            suppliers.save(Supplier.builder()
                    .supplierCode(r[0]).name(r[1]).managerName(r[2]).contact(r[3])
                    .leadTimeDays(Integer.parseInt(r[4])).active(true).memo(r[5]).build());
        }
    }
}
