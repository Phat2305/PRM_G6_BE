package com.example.art_course.modules.withdrawal.service;

import com.example.art_course.auth.repository.AuthRepository;
import com.example.art_course.common.AppError;
import com.example.art_course.common.BankAccountInfo;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.UUID;

// FIN-04: tiền rút chỉ chuyển về tài khoản ngân hàng trong hồ sơ giáo viên, không nhận tài khoản khác từ yêu cầu.
@Service
@RequiredArgsConstructor
public class WithdrawalBankAccount {

    private final AuthRepository authRepository;

    // requested có thì phải trùng hồ sơ (client cũ vẫn gửi); không có thì dùng hồ sơ. Trả về tài khoản để ghi vào yêu cầu rút.
    public String resolve(UUID teacherId, String requested) {
        String saved = authRepository.findTeacherProfile(teacherId)
                .map(AuthRepository.ProfileRow::bankAccountInfo).orElse("").trim();
        if (saved.isEmpty()) {
            throw new AppError(HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_REQUIRED",
                    "Hãy cập nhật tài khoản ngân hàng trong hồ sơ trước khi rút tiền");
        }
        // Hồ sơ cũ nhập tự do chưa đúng định dạng thì phải cập nhật lại, để Admin biết chuyển khoản cho ai.
        if (BankAccountInfo.split(saved) == null) {
            throw new AppError(HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_INVALID",
                    "Tài khoản ngân hàng trong hồ sơ chưa đúng định dạng, hãy cập nhật lại");
        }
        if (requested != null && !requested.isBlank()
                && !BankAccountInfo.normalizeName(requested).equals(BankAccountInfo.normalizeName(saved))) {
            throw new AppError(HttpStatus.BAD_REQUEST, "BANK_ACCOUNT_MISMATCH",
                    "Tiền chỉ được rút về tài khoản trong hồ sơ. Hãy làm mới hồ sơ rồi thử lại");
        }
        return saved;
    }
}
