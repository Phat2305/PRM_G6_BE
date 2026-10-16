import sys
import json
import base64
import hmac
import hashlib
import time

if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')


def base64url_encode(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode('utf-8').rstrip('=')

def create_jwt(user_id: str, role: str, secret: str, expires_in_seconds: int = 86400 * 30) -> str:
    header = {"alg": "HS256", "typ": "JWT"}
    payload = {
        "userId": user_id,
        "role": role,
        "type": "ACCESS",
        "iat": int(time.time()),
        "exp": int(time.time()) + expires_in_seconds
    }
    
    header_b64 = base64url_encode(json.dumps(header, separators=(',', ':')).encode('utf-8'))
    payload_b64 = base64url_encode(json.dumps(payload, separators=(',', ':')).encode('utf-8'))
    
    signing_input = f"{header_b64}.{payload_b64}".encode('utf-8')
    signature = hmac.new(secret.encode('utf-8'), signing_input, hashlib.sha256).digest()
    sig_b64 = base64url_encode(signature)
    
    return f"{header_b64}.{payload_b64}.{sig_b64}"

import os

def main():
    secret = os.environ.get("JWT_SECRET")
    if not secret and len(sys.argv) >= 2:
        secret = sys.argv[1]

    if not secret:
        print("[HƯỚNG DẪN] Cần cung cấp JWT_SECRET qua biến môi trường hoặc tham số dòng lệnh:")
        print("  Cách 1 (Khuyên dùng): $env:JWT_SECRET = \"your-secret-key-at-least-32-chars\"; python scripts/gen_tokens.py")
        print("  Cách 2: python scripts/gen_tokens.py \"your-secret-key-at-least-32-chars\"")
        sys.exit(1)
        
    if len(secret.encode('utf-8')) < 32:
        print("[LỖI] JWT_SECRET phải có độ dài tối thiểu 32 ký tự (256 bits) để đảm bảo an toàn HMAC-SHA256.")
        sys.exit(1)
        
    users = [
        {"role": "PARENT",  "userId": "11111111-1111-1111-1111-111111111111", "desc": "Phụ huynh mẫu"},
        {"role": "TEACHER", "userId": "22222222-2222-2222-2222-222222222222", "desc": "Giáo viên mẫu"},
        {"role": "STAFF",   "userId": "33333333-3333-3333-3333-333333333333", "desc": "Nhân viên vận hành mẫu"},
        {"role": "ADMIN",   "userId": "44444444-4444-4444-4444-444444444444", "desc": "Quản trị viên mẫu"}
    ]
    
    print("=" * 80)
    print("  BỘ TEST TOKEN JWT HS256 CHO CÁC VAI TRÒ (HẠN DÙNG 30 NGÀY)")
    print("=" * 80)
    for u in users:
        token = create_jwt(u["userId"], u["role"], secret)
        print(f"\n[+] VAI TRÒ: {u['role']} ({u['desc']})")
        print(f"    UserId: {u['userId']}")
        print(f"    Token:  {token}")
        print(f"    Header: Authorization: Bearer {token}")
    print("\n" + "=" * 80)

if __name__ == "__main__":
    main()
