package com.example.art_course.supply;

import com.example.art_course.supply.dto.CreateSupplyRequest;
import com.example.art_course.supply.dto.UpdateSupplyRequest;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.supply.service.SupplyService;
import com.example.art_course.user.entity.User;
import com.example.art_course.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@DisplayName("Dụng cụ: trạng thái do server quyết định theo kiểm duyệt và tồn kho")
class SupplyStockStatusTests {

    private final SupplyRepository supplyRepo = mock(SupplyRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final com.example.art_course.common.TeacherNames names = mock(com.example.art_course.common.TeacherNames.class);
    private final SupplyService service = new SupplyService(supplyRepo, userRepo, names);
    private final UUID teacherId = UUID.randomUUID();
    private final UUID supplyId = UUID.randomUUID();
    private Supply supply;

    @BeforeEach
    void setUp() {
        User teacher = new User();
        teacher.setId(teacherId);
        when(userRepo.findById(teacherId)).thenReturn(Optional.of(teacher));
        when(supplyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        supply = Supply.builder()
                .id(supplyId).teacherId(teacherId).name("Cọ").description("Mềm")
                .price(new BigDecimal("35000.00")).stockQuantity(3).status("ACTIVE").build();
        when(supplyRepo.findById(supplyId)).thenReturn(Optional.of(supply));
    }

    private UpdateSupplyRequest update(String name, String price, int stock, String clientStatus) {
        UpdateSupplyRequest r = new UpdateSupplyRequest();
        r.setName(name);
        r.setDescription("Mềm");
        r.setPrice(new BigDecimal(price));
        r.setStockQuantity(stock);
        r.setStatus(clientStatus);
        return r;
    }

    @Test
    void supplyListReturnsProfileNamesInApiResponseWithOneLookup() {
        UUID otherTeacherId = UUID.randomUUID();
        Supply second = Supply.builder().id(UUID.randomUUID()).teacherId(teacherId).name("Giấy").build();
        Supply missingProfile = Supply.builder().id(UUID.randomUUID()).teacherId(otherTeacherId).name("Màu").build();
        when(supplyRepo.findAll()).thenReturn(List.of(supply, second, missingProfile));
        when(names.byIds(List.of(teacherId, otherTeacherId))).thenReturn(Map.of(teacherId, "Cô Lan"));

        var response = new com.example.art_course.supply.Controller.SupplyController(service).getAllSupplies();

        assertEquals("Cô Lan", response.get(0).getTeacherName());
        assertEquals("Cô Lan", response.get(1).getTeacherName());
        assertNull(response.get(2).getTeacherName());
        assertEquals(supplyId, response.get(0).getId());
        verify(names).byIds(List.of(teacherId, otherTeacherId));
    }

    @Test
    @DisplayName("giáo viên tạo mới tự đặt ACTIVE vẫn bị đưa về PENDING_REVIEW")
    void createIgnoresClientStatus() {
        when(supplyRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        CreateSupplyRequest r = new CreateSupplyRequest();
        r.setName("Màu nước");
        r.setPrice(new BigDecimal("10000"));
        r.setStockQuantity(5);
        r.setStatus("ACTIVE");
        assertEquals("PENDING_REVIEW", service.createSupply(r, teacherId.toString()).getStatus());
    }

    @Test
    @DisplayName("tồn kho về 0 thì ACTIVE chuyển OUT_OF_STOCK, nhập thêm thì tự về ACTIVE, không cần duyệt lại")
    void stockOnlyChangeTogglesStatus() {
        assertEquals("OUT_OF_STOCK",
                service.updateSupply(supplyId, update("Cọ", "35000", 0, "ACTIVE"), teacherId.toString()).getStatus());
        assertEquals("ACTIVE",
                service.updateSupply(supplyId, update("Cọ", "35000", 7, "PENDING_REVIEW"), teacherId.toString()).getStatus());
    }

    @Test
    @DisplayName("đổi tên hoặc giá thì quay lại PENDING_REVIEW, status client gửi bị bỏ qua")
    void contentChangeNeedsReview() {
        assertEquals("PENDING_REVIEW",
                service.updateSupply(supplyId, update("Cọ lớn", "35000", 3, "ACTIVE"), teacherId.toString()).getStatus());
        supply.setStatus("ACTIVE");
        assertEquals("PENDING_REVIEW",
                service.updateSupply(supplyId, update("Cọ lớn", "40000", 3, "ACTIVE"), teacherId.toString()).getStatus());
    }

    @Test
    @DisplayName("nhập thêm hàng không gỡ trạng thái ẩn hoặc từ chối của kiểm duyệt")
    void restockDoesNotUnhide() {
        supply.setStatus("HIDDEN");
        assertEquals("HIDDEN",
                service.updateSupply(supplyId, update("Cọ", "35000", 9, "ACTIVE"), teacherId.toString()).getStatus());
    }

    @Test
    @DisplayName("BE2: thanh toán hết kho thì OUT_OF_STOCK, hoàn kho thì ACTIVE lại")
    void be2EntitySync() {
        com.example.art_course.supply.entity.Supply s =
                com.example.art_course.supply.entity.Supply.builder()
                        .stockQuantity(0).status("ACTIVE").build();
        s.syncStatusWithStock();
        assertEquals("OUT_OF_STOCK", s.getStatus());
        s.setStockQuantity(2);
        s.syncStatusWithStock();
        assertEquals("ACTIVE", s.getStatus());
        s.setStatus("PENDING_REVIEW");
        s.setStockQuantity(0);
        s.syncStatusWithStock();
        assertEquals("PENDING_REVIEW", s.getStatus());
    }
}
