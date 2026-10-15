export interface IEnrollmentContract {
  grantAccess(childId: string, courseId: string, orderId: string): Promise<void>;
  revokeAccess(childId: string, courseId: string, refundId: string): Promise<void>;
  isEnrolled(childId: string, courseId: string): Promise<boolean>;
}

export class MockEnrollmentService implements IEnrollmentContract {
  async grantAccess(_childId: string, _courseId: string, _orderId: string): Promise<void> {
    // Gọi sang BE1 hoặc cập nhật child_enroll
  }

  async revokeAccess(_childId: string, _courseId: string, _refundId: string): Promise<void> {
    // Thu hồi quyền học
  }

  async isEnrolled(_childId: string, _courseId: string): Promise<boolean> {
    return false;
  }
}
