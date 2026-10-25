package com.example.art_course.modules.finance;

import com.example.art_course.common.TeacherNames;
import com.example.art_course.common.statemachine.WithdrawalStatus;
import com.example.art_course.modules.finance.controller.AdminFinanceController;
import com.example.art_course.modules.systemconfig.service.ConfigService;
import com.example.art_course.modules.withdrawal.dto.WithdrawalResponseDto;
import com.example.art_course.modules.withdrawal.service.WithdrawalService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@DisplayName("Admin xem yêu cầu rút tiền: có họ tên giáo viên")
class AdminWithdrawalListTest {

    @Test
    @DisplayName("mỗi yêu cầu được gắn teacherName; giáo viên chưa có hồ sơ thì để null")
    void attachesTeacherNames() {
        WithdrawalService withdrawals = mock(WithdrawalService.class);
        TeacherNames names = mock(TeacherNames.class);
        AdminFinanceController controller = new AdminFinanceController(withdrawals, mock(ConfigService.class), names);

        UUID named = UUID.randomUUID();
        UUID unnamed = UUID.randomUUID();
        List<WithdrawalResponseDto> list = new ArrayList<>(List.of(
                WithdrawalResponseDto.builder().id(UUID.randomUUID()).teacherId(named)
                        .totalAmount(new BigDecimal("100000")).status(WithdrawalStatus.PENDING).build(),
                WithdrawalResponseDto.builder().id(UUID.randomUUID()).teacherId(unnamed)
                        .totalAmount(new BigDecimal("50000")).status(WithdrawalStatus.PENDING).build()));
        when(withdrawals.getAllWithdrawals(null)).thenReturn(list);
        when(names.byIds(any())).thenReturn(Map.of(named, "Cô Lan"));

        List<WithdrawalResponseDto> result = controller.listWithdrawals(null).getData();

        assertEquals("Cô Lan", result.get(0).getTeacherName());
        assertNull(result.get(1).getTeacherName());
    }
}
