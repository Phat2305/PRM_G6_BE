import { Request, Response, NextFunction } from 'express';
import jwt from 'jsonwebtoken';
import { env } from '../config/env';
import { AppError } from './error.middleware';
import { AuthUser, UserRole } from '../types/express';

/**
 * Middleware xác thực JWT access token.
 * Tài khoản Child tuyệt đối bị cấm truy cập các API thương mại và thanh toán (BR-02, NFR-04).
 */
export function requireAuth(req: Request, _res: Response, next: NextFunction): void {
  const authHeader = req.headers.authorization;

  if (!authHeader || !authHeader.startsWith('Bearer ')) {
    throw new AppError(401, 'UNAUTHORIZED', 'Vui lòng cung cấp access token trong header Authorization');
  }

  const token = authHeader.split(' ')[1];

  try {
    const decoded = jwt.verify(token, env.JWT_SECRET) as any;

    if (!decoded.userId || decoded.role === 'CHILD') {
      throw new AppError(403, 'FORBIDDEN', 'Tài khoản con không có quyền truy cập chức năng này (BR-02, NFR-04)');
    }

    req.user = {
      userId: decoded.userId,
      username: decoded.username || decoded.email,
      role: decoded.role as UserRole,
    };

    next();
  } catch (err: any) {
    if (err instanceof AppError) {
      throw err;
    }
    if (err.name === 'TokenExpiredError') {
      throw new AppError(401, 'TOKEN_EXPIRED', 'Access token đã hết hạn');
    }
    throw new AppError(401, 'INVALID_TOKEN', 'Access token không hợp lệ');
  }
}

/**
 * Middleware phân quyền theo vai trò người dùng (NFR-04)
 */
export function requireRoles(...allowedRoles: UserRole[]) {
  return (req: Request, _res: Response, next: NextFunction): void => {
    if (!req.user) {
      throw new AppError(401, 'UNAUTHORIZED', 'Chưa xác thực người dùng');
    }

    if (!allowedRoles.includes(req.user.role)) {
      throw new AppError(403, 'FORBIDDEN', `Vai trò "${req.user.role}" không có quyền truy cập chức năng này`);
    }

    next();
  };
}
