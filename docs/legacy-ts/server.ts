import { app } from './app';
import { env } from './config/env';

const server = app.listen(env.PORT, () => {
  console.log(`Server đang chạy tại http://localhost:${env.PORT}`);
});

process.on('SIGTERM', () => {
  console.log('Nhận tín hiệu SIGTERM, đang tắt gracefully...');
  server.close(() => {
    console.log('Server đã dừng.');
    process.exit(0);
  });
});
