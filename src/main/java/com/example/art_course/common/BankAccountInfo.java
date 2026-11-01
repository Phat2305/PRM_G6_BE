package com.example.art_course.common;

import org.springframework.http.HttpStatus;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

// Tài khoản nhận tiền của giáo viên, lưu một chuỗi "Ngân hàng - số tài khoản - chủ tài khoản" (FIN-04).
// Tên chủ tài khoản thường viết hoa không dấu còn họ tên trong hồ sơ có dấu, nên so khớp bỏ dấu và hoa thường.
public final class BankAccountInfo {

    private static final String SEPARATOR = " - ";
    private static final Pattern NUMBER = Pattern.compile("^\\d{6,20}$");
    private static final Pattern HOLDER = Pattern.compile("^\\p{L}[\\p{L} .'’-]*$");
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    public record Parts(String bank, String number, String holder) {
        public String format() {
            return bank + SEPARATOR + number + SEPARATOR + holder;
        }
    }

    private BankAccountInfo() {
    }

    // Bỏ dấu (kể cả đ/Đ), viết hoa, gộp khoảng trắng: "Nguyễn  Thị Lan" và "NGUYEN THI LAN" cho cùng kết quả.
    public static String normalizeName(String value) {
        if (value == null) {
            return "";
        }
        String plain = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("")
                .replace('đ', 'd').replace('Đ', 'D');
        return SPACES.matcher(plain.trim()).replaceAll(" ").toUpperCase(Locale.ROOT);
    }

    // Tách theo hai dấu " - " đầu tiên (tên chủ tài khoản được chứa dấu gạch); null nếu không đủ ba phần.
    public static Parts split(String raw) {
        if (raw == null) {
            return null;
        }
        int first = raw.indexOf(SEPARATOR);
        int second = first < 0 ? -1 : raw.indexOf(SEPARATOR, first + SEPARATOR.length());
        if (second < 0) {
            return null;
        }
        return new Parts(
                SPACES.matcher(raw.substring(0, first).trim()).replaceAll(" "),
                raw.substring(first + SEPARATOR.length(), second).replaceAll("\\s", ""),
                SPACES.matcher(raw.substring(second + SEPARATOR.length()).trim()).replaceAll(" "));
    }

    // Kiểm tra định dạng và, khi biết họ tên hồ sơ, tên chủ tài khoản phải trùng (không phân biệt dấu). Trả về chuỗi chuẩn hóa để lưu.
    public static String validate(String raw, String fullName) {
        Parts parts = split(raw);
        if (parts == null) {
            throw invalid("BANK_ACCOUNT_INVALID",
                    "Nhập đủ ngân hàng, số tài khoản và tên chủ tài khoản, ngăn cách bằng \" - \"");
        }
        if (parts.bank().length() < 2 || parts.bank().length() > 50) {
            throw invalid("BANK_ACCOUNT_INVALID", "Tên ngân hàng gồm 2-50 ký tự");
        }
        if (!NUMBER.matcher(parts.number()).matches()) {
            throw invalid("BANK_ACCOUNT_INVALID", "Số tài khoản gồm 6-20 chữ số");
        }
        if (parts.holder().length() < 2 || parts.holder().length() > 100
                || !HOLDER.matcher(parts.holder()).matches()) {
            throw invalid("BANK_ACCOUNT_INVALID", "Tên chủ tài khoản chỉ gồm chữ cái, 2-100 ký tự");
        }
        if (fullName != null && !fullName.isBlank()
                && !normalizeName(parts.holder()).equals(normalizeName(fullName))) {
            throw invalid("BANK_HOLDER_MISMATCH",
                    "Tên chủ tài khoản phải trùng họ tên trong hồ sơ (không phân biệt hoa thường và dấu)");
        }
        return parts.format();
    }

    private static AppError invalid(String code, String message) {
        return new AppError(HttpStatus.BAD_REQUEST, code, message);
    }
}
