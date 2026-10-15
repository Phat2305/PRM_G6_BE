# Hướng dẫn Sao lưu & Khôi phục Cơ sở dữ liệu (NFR-15)

Tài liệu này đặc tả quy trình và script sao lưu (backup) & khôi phục (disaster recovery) cho PostgreSQL của hệ thống Backend 2.

---

## 1. Chiến lược Sao lưu (Backup Policy)

1. **Sao lưu Toàn phần Hằng ngày (Full Daily Dump)**:
   - Thời gian chạy: 01:00 AM mỗi ngày (giờ thấp điểm).
   - Định dạng: Custom compressed format (`-Fc`) của `pg_dump` để hỗ trợ nén và khôi phục linh hoạt.
   - Thời gian lưu trữ (Retention): Lưu trữ tối thiểu 30 ngày trên hạ tầng lưu trữ đám mây độc lập (S3 / Cloud Storage có bật versioning).
2. **Sổ cái `money_ledger` và `payments`**:
   - Bảng `money_ledger` có trigger chống UPDATE/DELETE (`trg_prevent_money_ledger_modification`).
   - Kiểm tra tính toàn vẹn (Checksums) định kỳ sau mỗi lần sao lưu.

---

## 2. Script Tự động Sao lưu (`backup.sh`)

```bash
#!/bin/bash
set -eo pipefail

TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_DIR="/var/backups/art_course_db"
BACKUP_FILE="${BACKUP_DIR}/art_course_prod_${TIMESTAMP}.dump"

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Bắt đầu sao lưu cơ sở dữ liệu PostgreSQL..."

# Thực hiện pg_dump với định dạng nén custom (-Fc)
PGPASSWORD="${DB_PASSWORD}" pg_dump \
  -h "${DB_HOST}" \
  -p "${DB_PORT:-5432}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  -Fc \
  -b -v \
  -f "${BACKUP_FILE}"

echo "[$(date)] Sao lưu hoàn tất: ${BACKUP_FILE}"

# Tính mã băm SHA256 để bảo toàn tính toàn vẹn
sha256sum "${BACKUP_FILE}" > "${BACKUP_FILE}.sha256"

# Xóa các file sao lưu cũ hơn 30 ngày
find "${BACKUP_DIR}" -type f -name "*.dump" -mtime +30 -exec rm -f {} \;
find "${BACKUP_DIR}" -type f -name "*.sha256" -mtime +30 -exec rm -f {} \;

echo "[$(date)] Hoàn tất dọn dẹp các bản sao lưu cũ."
```

---

## 3. Quy trình Khôi phục Thảm họa (Disaster Recovery - `restore.sh`)

```bash
#!/bin/bash
set -eo pipefail

BACKUP_FILE="$1"

if [ -z "${BACKUP_FILE}" ] || [ ! -f "${BACKUP_FILE}" ]; then
  echo "LỖI: Vui lòng chỉ định đường dẫn file sao lưu hợp lệ (.dump)!"
  echo "Cách dùng: ./restore.sh /path/to/backup.dump"
  exit 1
fi

echo "[$(date)] Bắt đầu xác thực tính toàn vẹn file sao lưu..."
if [ -f "${BACKUP_FILE}.sha256" ]; then
  sha256sum -c "${BACKUP_FILE}.sha256"
  echo "Checksum SHA256 khớp 100%!"
fi

echo "[$(date)] Đang khôi phục database từ ${BACKUP_FILE}..."

# Khôi phục bằng pg_restore
PGPASSWORD="${DB_PASSWORD}" pg_restore \
  -h "${DB_HOST}" \
  -p "${DB_PORT:-5432}" \
  -U "${DB_USER}" \
  -d "${DB_NAME}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  -v "${BACKUP_FILE}"

echo "[$(date)] Khôi phục database thành công hoàn toàn!"
```

---

## 4. Kế hoạch Diễn tập Khôi phục (DR Drill)
- Thực hiện diễn tập khôi phục trên môi trường Staging/Dev mỗi quý một lần.
- Đảm bảo thời gian khôi phục (RTO - Recovery Time Objective) dưới 30 phút.
- Mức thất thoát dữ liệu tối đa chấp nhận được (RPO - Recovery Point Objective) dưới 24 giờ.
