export type UserRole = 'PARENT' | 'TEACHER' | 'STAFF' | 'ADMIN' | 'CHILD';

export interface AuthUser {
  userId: string;
  username: string;
  role: UserRole;
}

declare global {
  namespace Express {
    interface Request {
      user?: AuthUser;
    }
  }
}
