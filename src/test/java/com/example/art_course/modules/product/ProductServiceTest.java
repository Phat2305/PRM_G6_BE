package com.example.art_course.modules.product;

import com.example.art_course.common.AppError;
import com.example.art_course.common.TeacherNames;
import com.example.art_course.modules.product.entity.Supply;
import com.example.art_course.modules.product.repository.SupplyRepository;
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
    private final ProductService service = new ProductService(repo, names);

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
