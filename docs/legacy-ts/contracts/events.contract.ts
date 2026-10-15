export type AppEventType =
  | 'course_completed'
  | 'course_reviewed'
  | 'customize_offer_accepted'
  | 'customize_completed'
  | 'submission_created'
  | 'submission_graded'
  | 'message_sent';

export interface AppEvent<T = any> {
  type: AppEventType;
  payload: T;
  timestamp: Date;
}

export type EventHandler<T = any> = (event: AppEvent<T>) => Promise<void>;

class SimpleEventBus {
  private handlers = new Map<AppEventType, EventHandler[]>();

  on<T>(type: AppEventType, handler: EventHandler<T>): void {
    if (!this.handlers.has(type)) {
      this.handlers.set(type, []);
    }
    this.handlers.get(type)!.push(handler);
  }

  async emit<T>(type: AppEventType, payload: T): Promise<void> {
    const event: AppEvent<T> = {
      type,
      payload,
      timestamp: new Date(),
    };
    const list = this.handlers.get(type) || [];
    for (const h of list) {
      try {
        await h(event);
      } catch (err) {
        console.error(`Lỗi khi xử lý sự kiện ${type}:`, err);
      }
    }
  }
}

export const eventBus = new SimpleEventBus();
