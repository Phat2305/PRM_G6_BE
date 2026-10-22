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

# Bước 2: Khởi chạy ứng dụng với Java 21
FROM eclipse-temurin:21-jre-jammy
EXPOSE 8080 
COPY --from=build /app/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
