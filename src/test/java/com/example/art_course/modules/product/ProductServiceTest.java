package com.example.art_course.modules.product;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.modules.product.service.ProductService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("Chi tiết dụng cụ công khai: chỉ dụng cụ đang bán hoặc hết hàng")
class ProductServiceTest {

    private final SupplyRepository repo = mock(SupplyRepository.class);
    private final TeacherNames names = mock(TeacherNames.class);
    private final com.example.art_course.modules.systemconfig.service.ConfigService config =
            mock(com.example.art_course.modules.systemconfig.service.ConfigService.class);
    private final ProductService service = new ProductService(repo, names, config);

    private UUID supplyWith(String status) {
        UUID id = UUID.randomUUID();
        when(repo.findById(id)).thenReturn(Optional.of(
                Supply.builder().id(id).teacherId(UUID.randomUUID()).status(status).build()));
        return id;
    }

    @Test
    @DisplayName("ACTIVE và OUT_OF_STOCK xem được (hết hàng vẫn hiện để FE ghi \"Hết hàng\")")
    void sellableStatusesAreVisible() {
        assertNotNull(service.getSupplyDetail(supplyWith("ACTIVE")));
        assertNotNull(service.getSupplyDetail(supplyWith("out_of_stock")));
    }

    @Test
    @DisplayName("phí ship hiện ra là phí riêng của giáo viên, chưa đặt thì lấy mặc định trong system_config")
    void shippingFeeIsTheTeacherFeeOrTheConfiguredDefault() {
        UUID own = UUID.randomUUID();
        UUID plain = UUID.randomUUID();
        when(config.getDefaultShippingFee()).thenReturn(com.example.art_course.common.Money.of(25000));
        when(names.shippingFeeOf(own)).thenReturn(40000L);
        when(names.shippingFeeOf(plain)).thenReturn(null);
        Supply a = Supply.builder().id(UUID.randomUUID()).teacherId(own).name("A").status("ACTIVE").build();
        Supply b = Supply.builder().id(UUID.randomUUID()).teacherId(plain).name("B").status("ACTIVE").build();
        when(repo.findByStatusInIgnoreCase(java.util.List.of("ACTIVE", "OUT_OF_STOCK"))).thenReturn(java.util.List.of(a, b));
        when(names.byIds(any())).thenReturn(java.util.Map.of());
        when(repo.findById(b.getId())).thenReturn(Optional.of(b));

        service.listSupplies();

        assertEquals(40000L, a.getShippingFee());
        assertEquals(25000L, b.getShippingFee());
        assertEquals(25000L, service.getSupplyDetail(b.getId()).getShippingFee());
    }

    @Test
    @DisplayName("chờ duyệt, bị ẩn hoặc bị từ chối: 404 như không tồn tại, không lộ cho người chưa đăng nhập")
    void unpublishedStatusesAreHidden() {
        for (String status : new String[] {"PENDING_REVIEW", "INACTIVE", "REJECTED", null}) {
            UUID id = supplyWith(status);
            AppError error = assertThrows(AppError.class, () -> service.getSupplyDetail(id), String.valueOf(status));
            assertEquals(404, error.getStatus().value());
        }
        assertThrows(AppError.class, () -> service.getSupplyDetail(UUID.randomUUID()));
    }
}
