package com.example.art_course.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// FIN-04: tài khoản nhận tiền đúng định dạng; tên chủ tài khoản không dấu vẫn khớp họ tên có dấu.
class BankAccountInfoTest {

    private static AppError rejected(String raw, String fullName) {
        return assertThrows(AppError.class, () -> BankAccountInfo.validate(raw, fullName));
    }

    @Test
    void unaccentedUpperCaseHolderMatchesAccentedFullName() {
        assertEquals("VCB - 0123456789 - NGUYEN THI LAN",
                BankAccountInfo.validate("VCB - 0123456789 - NGUYEN THI LAN", "Nguyễn Thị Lan"));
    }

    @Test
    void accentedHolderAndLowerCaseAlsoMatch() {
        assertEquals("MB - 123456 - nguyễn thị lan",
                BankAccountInfo.validate("MB - 123456 - nguyễn thị lan", "NGUYEN THI LAN"));
        assertDoesNotThrow(() -> BankAccountInfo.validate("MB - 123456 - Đặng Văn Đức", "DANG VAN DUC"));
    }

    @Test
    void decomposedUnicodeAndExtraSpacesMatch() {
        String decomposed = "Nguyễn Thị Lan";
        assertDoesNotThrow(() -> BankAccountInfo.validate("VCB - 0123456789 - NGUYEN   THI LAN", decomposed));
    }

    @Test
    void differentNameIsRejected() {
        assertEquals("BANK_HOLDER_MISMATCH", rejected("VCB - 0123456789 - TRAN VAN BINH", "Nguyễn Thị Lan").getErrorCode());
    }

    @Test
    void numberMustBeSixToTwentyDigits() {
        for (String number : new String[]{"12345", "123456789012345678901", "12a456", "••••1234"}) {
            assertEquals("BANK_ACCOUNT_INVALID",
                    rejected("VCB - " + number + " - LAN", "Lan").getErrorCode(), number);
        }
        assertDoesNotThrow(() -> BankAccountInfo.validate("VCB - 123456 - LAN", "Lan"));
        assertDoesNotThrow(() -> BankAccountInfo.validate("VCB - 12345678901234567890 - LAN", "Lan"));
    }

    @Test
    void needsAllThreePartsAndSaneNames() {
        for (String raw : new String[]{null, "", "VCB", "VCB - 0123456789", "V - 0123456789 - LAN",
                "VCB - 0123456789 - L4N", "VCB - 0123456789 - 12"}) {
            assertEquals("BANK_ACCOUNT_INVALID", rejected(raw, null).getErrorCode(), String.valueOf(raw));
        }
    }

    @Test
    void numberSpacesAreDroppedAndSpacesCollapsedInTheStoredValue() {
        assertEquals("Vietcombank - 0123456789 - NGUYEN THI LAN",
                BankAccountInfo.validate("  Vietcombank  - 0123 4567 89 -  NGUYEN   THI LAN ", "Nguyễn Thị Lan"));
    }

    @Test
    void holderMayContainTheSeparatorCharacters() {
        var parts = BankAccountInfo.split("VCB - 0123456789 - NGUYEN THI - LAN");
        assertEquals("NGUYEN THI - LAN", parts.holder());
    }

    @Test
    void withoutFullNameOnlyTheFormatIsChecked() {
        assertDoesNotThrow(() -> BankAccountInfo.validate("VCB - 0123456789 - ANYONE", null));
    }
}
