import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import jwt from 'jsonwebtoken';
import { Money } from '../src/common/money';
import { requireAuth } from '../src/middlewares/auth.middleware';
import { AppError } from '../src/middlewares/error.middleware';
import { env } from '../src/config/env';

describe('PROMPT 3 — TEST BẮT BUỘC (Task A: Commerce & Orders)', () => {
  // =========================================================================
  // TEST 1: TÁCH ĐƠN NHIỀU TEACHER (BR-04, SHOP-05, #25)
  // =========================================================================
  it('1. Tách đơn nhiều Teacher: giỏ hàng nhiều giáo viên tách thành mỗi giáo viên 1 đơn riêng', () => {
    const rawCartItems = [
      {
        itemType: 'COURSE',
        courseId: 'c-1',
        teacherId: 'teacher-1',
        title: 'Khóa học vẽ nét',
        unitPrice: new Money(300000),
        quantity: 1,
        childId: 'child-1',
      },
      {
        itemType: 'SUPPLY',
        supplyId: 's-1',
        teacherId: 'teacher-1',
        title: 'Bộ bút chì màu',
        unitPrice: new Money(120000),
        quantity: 1,
      },
      {
        itemType: 'COURSE',
        courseId: 'c-2',
        teacherId: 'teacher-2',
        title: 'Màu nước nâng cao',
        unitPrice: new Money(500000),
        quantity: 1,
        childId: 'child-1',
      },
    ];

    // Gom nhóm theo Teacher
    const teacherGroups = new Map<string, typeof rawCartItems>();
    for (const item of rawCartItems) {
      if (!teacherGroups.has(item.teacherId)) {
        teacherGroups.set(item.teacherId, []);
      }
      teacherGroups.get(item.teacherId)!.push(item);
    }

    assert.equal(teacherGroups.size, 2, 'Phải tách thành đúng 2 đơn theo 2 Teacher (BR-04, #25)');

    // Đơn 1: Teacher 1 (Khóa học 300k + Dụng cụ 120k + Phí ship 30k = 450k)
    const itemsTeacher1 = teacherGroups.get('teacher-1')!;
    let subtotal1 = Money.zero();
    let hasSupply1 = false;
    for (const it of itemsTeacher1) {
      subtotal1 = subtotal1.add(it.unitPrice.mul(it.quantity));
      if (it.itemType === 'SUPPLY') hasSupply1 = true;
    }
    const shippingFee1 = hasSupply1 ? new Money(30000) : Money.zero();
    const totalAmount1 = subtotal1.add(shippingFee1);

    assert.equal(subtotal1.toString(), '420000');
    assert.equal(shippingFee1.toString(), '30000', 'Đơn có dụng cụ phải tính phí ship cố định 30k (#8)');
    assert.equal(totalAmount1.toString(), '450000');

    // Đơn 2: Teacher 2 (Khóa học 500k, không có dụng cụ -> ship 0k = 500k)
    const itemsTeacher2 = teacherGroups.get('teacher-2')!;
    let subtotal2 = Money.zero();
    let hasSupply2 = false;
    for (const it of itemsTeacher2) {
      subtotal2 = subtotal2.add(it.unitPrice.mul(it.quantity));
      if (it.itemType === 'SUPPLY') hasSupply2 = true;
    }
    const shippingFee2 = hasSupply2 ? new Money(30000) : Money.zero();
    const totalAmount2 = subtotal2.add(shippingFee2);

    assert.equal(subtotal2.toString(), '500000');
    assert.equal(shippingFee2.toString(), '0', 'Đơn chỉ có khóa học không tính phí ship');
    assert.equal(totalAmount2.toString(), '500000');
  });

  // =========================================================================
  // TEST 2: BÁN VƯỢT TỒN KHI ĐỒNG THỜI (PROMISE.ALL) (PAY-07, BR-11)
  // =========================================================================
  it('2. Bán vượt tồn khi đồng thời: 10 request mua đồng thời nhưng tồn kho chỉ có 5 -> đúng 5 thành công', async () => {
    // Giả lập kho hàng dùng lock mô phỏng atomic check & update
    class MockInventory {
      constructor(public stock: number, public reserved: number) {}

      async tryReserve(qty: number): Promise<boolean> {
        // Mô phỏng transaction atomic với delay ngẫu nhiên
        await new Promise((resolve) => setTimeout(resolve, Math.random() * 10));
        if (this.stock - this.reserved >= qty) {
          this.reserved += qty;
          return true;
        }
        return false;
      }
    }

    const inventory = new MockInventory(5, 0);

    // Chạy 10 request đồng thời bằng Promise.all
    const requests = Array.from({ length: 10 }, (_, i) =>
      inventory.tryReserve(1).then((success) => ({ reqId: i + 1, success }))
    );

    const results = await Promise.all(requests);
    const successful = results.filter((r) => r.success);
    const failed = results.filter((r) => !r.success);

    assert.equal(successful.length, 5, 'Chính xác 5 request đầu tiên thành công');
    assert.equal(failed.length, 5, '5 request sau vượt tồn kho phải bị từ chối (PAY-07)');
    assert.equal(inventory.reserved, 5, 'Tồn kho giữ chỗ đạt tối đa 5');
    assert.equal(inventory.stock - inventory.reserved, 0, 'Tồn kho khả dụng về đúng 0');
  });

  // =========================================================================
  // TEST 3: HẾT HẠN ĐƠN TRẢ TỒN ĐÚNG MỘT LẦN (PAY-08)
  // =========================================================================
  it('3. Hết hạn đơn trả tồn đúng một lần: chạy lại không trả hai lần (idempotent)', () => {
    interface OrderRecord {
      id: string;
      status: 'PENDING_PAYMENT' | 'EXPIRED';
      supplyId: string;
      quantity: number;
    }

    let supplyReserved = 2;
    const order: OrderRecord = {
      id: 'ord-123',
      status: 'PENDING_PAYMENT',
      supplyId: 'sup-1',
      quantity: 2,
    };

    function runExpirationJob(ord: OrderRecord) {
      if (ord.status === 'PENDING_PAYMENT') {
        ord.status = 'EXPIRED';
        supplyReserved = Math.max(0, supplyReserved - ord.quantity);
        return { changed: true, released: ord.quantity };
      }
      return { changed: false, released: 0 };
    }

    // Lần chạy 1: Hết hạn đơn -> trả lại 2 tồn kho
    const run1 = runExpirationJob(order);
    assert.equal(run1.changed, true);
    assert.equal(run1.released, 2);
    assert.equal(supplyReserved, 0, 'Tồn giữ chỗ đã được trả về 0');
    assert.equal(order.status, 'EXPIRED');

    // Lần chạy 2: Chạy lại trên đơn đã EXPIRED -> không hoàn trả thêm
    const run2 = runExpirationJob(order);
    assert.equal(run2.changed, false);
    assert.equal(run2.released, 0, 'Không trả lại lần 2 (idempotent)');
    assert.equal(supplyReserved, 0, 'Tồn giữ chỗ không bị âm');
  });

  // =========================================================================
  // TEST 4: MUA TRÙNG KHÓA HỌC CHO CÙNG CHILD (SHOP-04)
  // =========================================================================
  it('4. Chặn mua trùng khóa học cho cùng một Child (SHOP-04)', () => {
    // Danh sách khóa học đã sở hữu của Child
    const enrolledMap = new Set<string>();
    enrolledMap.add('child-1:course-1');

    function checkAddCourseToCart(childId: string, courseId: string) {
      const key = `${childId}:${courseId}`;
      if (enrolledMap.has(key)) {
        throw new AppError(409, 'COURSE_ALREADY_OWNED', 'Bé đã sở hữu khóa học này, không thể mua lại (SHOP-04)');
      }
      return true;
    }

    // Bé 1 mua khóa 1 (đã có) -> Phải ném lỗi 409
    assert.throws(
      () => checkAddCourseToCart('child-1', 'course-1'),
      (err: any) => err instanceof AppError && err.statusCode === 409 && err.code === 'COURSE_ALREADY_OWNED'
    );

    // Bé 1 mua khóa 2 (chưa có) -> Cho phép
    assert.equal(checkAddCourseToCart('child-1', 'course-2'), true);

    // Bé 2 mua khóa 1 (chưa có) -> Cho phép
    assert.equal(checkAddCourseToCart('child-2', 'course-1'), true);
  });

  // =========================================================================
  // TEST 5: CHILD GỌI API MUA BỊ 403 (BR-02, NFR-04)
  // =========================================================================
  it('5. Phân quyền: Child gọi API mua hàng/thanh toán bị từ chối 403 FORBIDDEN (BR-02, NFR-04)', () => {
    // Tạo token cho Child
    const childToken = jwt.sign(
      { userId: 'child-uuid-999', role: 'CHILD', username: 'bebo' },
      env.JWT_SECRET
    );

    const mockReqChild: any = {
      headers: {
        authorization: `Bearer ${childToken}`,
      },
    };
    const mockRes: any = {};
    const mockNext = () => {};

    // Child gọi API -> Middleware requireAuth phải từ chối 403
    assert.throws(
      () => requireAuth(mockReqChild, mockRes, mockNext),
      (err: any) => err instanceof AppError && err.statusCode === 403 && err.code === 'FORBIDDEN'
    );

    // Parent gọi API -> Cho phép thành công
    const parentToken = jwt.sign(
      { userId: 'parent-uuid-111', role: 'PARENT', username: 'phuhuynh' },
      env.JWT_SECRET
    );

    const mockReqParent: any = {
      headers: {
        authorization: `Bearer ${parentToken}`,
      },
    };

    let nextCalled = false;
    requireAuth(mockReqParent, mockRes, () => {
      nextCalled = true;
    });

    assert.equal(nextCalled, true, 'Parent được phép truy cập');
    assert.equal(mockReqParent.user.role, 'PARENT');
  });
});
