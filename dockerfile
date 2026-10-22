# Bước 1: Build source code
FROM gradle:8.5-jdk17 AS build
WORKDIR /home/gradle/src

# TỐI ƯU 1: Chỉ copy các file cấu hình thư viện vào trước
COPY build.gradle settings.gradle ./
# Nếu dự án có thư mục gradle (chứa wrapper), hãy mở comment dòng dưới
# COPY gradle ./gradle 

# TỐI ƯU 2: Tải trước các thư viện. 
# Bước này sẽ được Docker "nhớ" (cache) lại cho các lần push sau, 
# MIỄN LÀ bạn không thêm/sửa thư viện trong file build.gradle.
RUN gradle dependencies --no-daemon || true

# TỐI ƯU 3: Copy phần source code (thứ hay thay đổi nhất) vào sau cùng
COPY src ./src

# Build file .jar (lúc này thư viện đã có sẵn từ cache, build rất nhanh)
RUN gradle build --no-daemon -x test

# Bước 2: Khởi chạy ứng dụng
FROM eclipse-temurin:17-jre-jammy
EXPOSE 8080 
COPY --from=build /home/gradle/src/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]