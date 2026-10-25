-- Hội thoại Teacher – Phụ huynh và báo giá trong chat (docs/chat-offer-plan.md, GĐ A–B).
-- Chạy MỘT LẦN trên Supabase (SQL Editor) TRƯỚC khi deploy BE có code mới.
-- Chạy lại nhiều lần vẫn an toàn (IF NOT EXISTS). Chỉ thêm cột/ràng buộc, không xóa hay sửa dữ liệu.
-- Đã kiểm tra ngày 2026-10-25: không có hội thoại trùng (yêu cầu, giáo viên); chưa có tin báo giá nào.

begin;

-- 1. Gắn tin báo giá với báo giá (thay cho việc FE đoán "tin báo giá cuối là báo giá hiện hành").
alter table public.messages
    add column if not exists offer_id uuid;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'fk_messages_offer') then
        alter table public.messages
            add constraint fk_messages_offer foreign key (offer_id) references public.offers (id);
    end if;
end $$;

create index if not exists idx_messages_offer_id on public.messages (offer_id);
-- Đọc tin theo hội thoại, sắp theo thời gian (màn chat, hỏi tin mới ?after=).
create index if not exists idx_messages_conversation_created on public.messages (conversation_id, created_at);

-- 2. Số ngày bàn giao của báo giá (trước đây chỉ nằm trong chữ của lời nhắn).
alter table public.offers
    add column if not exists delivery_days integer;

do $$
begin
    if not exists (select 1 from pg_constraint where conname = 'chk_offers_delivery_days') then
        alter table public.offers
            add constraint chk_offers_delivery_days
                check (delivery_days is null or delivery_days between 1 and 60);
    end if;
end $$;

-- 3. Mốc "đã đọc tới đâu" của từng bên, để tính số tin chưa đọc.
alter table public.conversations
    add column if not exists parent_last_read_at timestamptz,
    add column if not exists teacher_last_read_at timestamptz;

-- 4. Mỗi giáo viên chỉ có một hội thoại cho một yêu cầu (hoặc một ngữ cảnh bất kỳ).
create unique index if not exists uq_conversations_context_teacher
    on public.conversations (context_type, context_id, teacher_id);

commit;

-- Kiểm tra sau khi chạy: phải ra đủ 5 cột mới.
select table_name, column_name, data_type
from information_schema.columns
where table_schema = 'public'
  and (table_name, column_name) in (
      ('messages', 'offer_id'),
      ('offers', 'delivery_days'),
      ('conversations', 'parent_last_read_at'),
      ('conversations', 'teacher_last_read_at'),
      ('messages', 'conversation_id'))
order by table_name, column_name;

-- Hoàn tác (chỉ dùng nếu cần bỏ hẳn thay đổi này; mất dữ liệu trong các cột mới):
-- begin;
-- drop index if exists public.uq_conversations_context_teacher;
-- alter table public.conversations drop column if exists parent_last_read_at, drop column if exists teacher_last_read_at;
-- alter table public.offers drop constraint if exists chk_offers_delivery_days;
-- alter table public.offers drop column if exists delivery_days;
-- drop index if exists public.idx_messages_conversation_created;
-- drop index if exists public.idx_messages_offer_id;
-- alter table public.messages drop constraint if exists fk_messages_offer;
-- alter table public.messages drop column if exists offer_id;
-- commit;
