# Bước 1: Build source code với Java 21
FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /app

# Copy các file cấu hình và wrapper vào trước
COPY gradle/ ./gradle/
COPY gradlew build.gradle settings.gradle ./

# Cấp quyền thực thi cho file gradlew
RUN chmod +x ./gradlew

# Tải trước các thư viện (lưu cache)
RUN ./gradlew dependencies --no-daemon || true

# Copy source code và build
COPY src ./src
RUN ./gradlew build --no-daemon -x test

# Bước 2: Khởi chạy ứng dụng với Java 21 (ghim 21.0.11 như máy local đang chạy được)
FROM eclipse-temurin:21.0.11_10-jre-jammy
# VideoProcessingService gọi `ffmpeg` qua PATH (app.video.ffmpeg-path)
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*
EXPOSE 8080
COPY --from=build /app/build/libs/*.jar app.jar
# Instance 512MB: giới hạn heap ~50% và dùng SerialGC để chừa RAM cho ffmpeg.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:+UseSerialGC", "-Xss512k", "-jar", "/app.jar", "--spring.profiles.active=supabase"]
