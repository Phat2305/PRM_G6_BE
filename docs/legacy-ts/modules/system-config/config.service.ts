import { query } from '../../config/db';

export interface SystemConfigValues {
  commissionRateCourse: string;
  commissionRateSupply: string;
  orderPaymentTimeoutMinutes: number;
  quotePaymentTimeoutHours: number;
  autoConfirmDeliveryDays: number;
  defaultShippingFee: number;
}

export class ConfigService {
  private static cachedConfig: SystemConfigValues | null = null;
  private static lastFetch: number = 0;
  private static CACHE_TTL_MS = 60 * 1000;

  static async getConfig(): Promise<SystemConfigValues> {
    const now = Date.now();
    if (this.cachedConfig && now - this.lastFetch < this.CACHE_TTL_MS) {
      return this.cachedConfig;
    }

    try {
      const res = await query('SELECT key, value FROM public.system_config');
      const map = new Map<string, string>();
      for (const row of res.rows) {
        map.set(row.key, row.value);
      }

      this.cachedConfig = {
        commissionRateCourse: map.get('commission_rate_course') || '0.20',
        commissionRateSupply: map.get('commission_rate_supply') || '0.10',
        orderPaymentTimeoutMinutes: parseInt(map.get('order_payment_timeout_minutes') || '15', 10),
        quotePaymentTimeoutHours: parseInt(map.get('quote_payment_timeout_hours') || '24', 10),
        autoConfirmDeliveryDays: parseInt(map.get('auto_confirm_delivery_days') || '7', 10),
        defaultShippingFee: parseInt(map.get('default_shipping_fee') || '30000', 10),
      };
      this.lastFetch = now;
      return this.cachedConfig;
    } catch {
      // Fallback khi DB chưa có
      return {
        commissionRateCourse: '0.20',
        commissionRateSupply: '0.10',
        orderPaymentTimeoutMinutes: 15,
        quotePaymentTimeoutHours: 24,
        autoConfirmDeliveryDays: 7,
        defaultShippingFee: 30000,
      };
    }
  }
}
