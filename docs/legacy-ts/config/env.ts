import dotenv from 'dotenv';
dotenv.config({ path: '.env.local' });
dotenv.config();

export const env = {
  PORT: parseInt(process.env.PORT || '3000', 10),
  NODE_ENV: process.env.NODE_ENV || 'development',
  DATABASE_URL_DEV: process.env.DATABASE_URL_DEV || '',
  JWT_SECRET: process.env.JWT_SECRET || 'art-course-secret-key-prm-g6-be-2026',
  JWT_EXPIRES_IN: process.env.JWT_EXPIRES_IN || '24h',
  ORDER_TIMEOUT_MINUTES: parseInt(process.env.ORDER_TIMEOUT_MINUTES || '15', 10),
  AUTO_DELIVERY_CONFIRM_DAYS: parseInt(process.env.AUTO_DELIVERY_CONFIRM_DAYS || '7', 10),
  DEFAULT_SHIPPING_FEE: parseInt(process.env.DEFAULT_SHIPPING_FEE || '30000', 10),
};
