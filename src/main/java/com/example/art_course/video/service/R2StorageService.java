
package com.example.art_course.video.service;

import jakarta.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class R2StorageService {

    private final S3Client s3Client;
    private final String bucket;

    public R2StorageService(
            @Value("${app.video.r2.endpoint}") String endpoint,
            @Value("${app.video.r2.bucket}") String bucket,
            @Value("${app.video.r2.access-key}") String accessKey,
            @Value("${app.video.r2.secret-key}") String secretKey) {

        this.bucket = bucket;

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                // trim: biến môi trường dán vào Render hay dính khoảng trắng/xuống dòng.
                                AwsBasicCredentials.create(
                                        accessKey.trim(),
                                        secretKey.trim())))
                .serviceConfiguration(
                        S3Configuration.builder()
                                .pathStyleAccessEnabled(true)
                                .build())
                .build();
    }

    public void uploadFile(
            String key,
            Path file,
            String contentType) {

        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Object key is required");
        }

        if (file == null
                || !Files.isRegularFile(file)
                || !Files.isReadable(file)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "File not found or unreadable");
        }

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromFile(file));

        } catch (S3Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 upload failed",
                    e);

        } catch (SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 request failed",
                    e);
        }
    }

    public void uploadBytes(
            String key,
            byte[] data,
            String contentType) {

        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType(contentType)
                            .build(),
                    RequestBody.fromBytes(data));

        } catch (S3Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 upload failed",
                    e);

        } catch (SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 request failed",
                    e);
        }
    }

    public ResponseInputStream<GetObjectResponse> getObject(
            String key) {

        if (key == null || key.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Object key is required");
        }

        try {
            return s3Client.getObject(
                    GetObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .build());

        } catch (S3Exception e) {
            if (e.statusCode() == 404
                    || "NoSuchKey".equals(e.awsErrorDetails() != null
                    ? e.awsErrorDetails().errorCode()
                    : null)) {
                throw new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Video file not found");
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cannot read file from Cloudflare R2",
                    e);

        } catch (SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 request failed",
                    e);
        }
    }

    // Xóa một tệp; không có thì coi như đã xóa (S3 trả thành công).
    public void deleteObject(String key) {
        try {
            s3Client.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 delete failed",
                    e);
        }
    }

    // Xóa mọi tệp có khóa bắt đầu bằng prefix (ví dụ thư mục HLS của một bài học). prefix phải kết thúc bằng "/".
    public void deletePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/")) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Prefix must end with /");
        }
        try {
            String token = null;
            do {
                ListObjectsV2Response page = s3Client.listObjectsV2(
                        ListObjectsV2Request.builder()
                                .bucket(bucket)
                                .prefix(prefix)
                                .continuationToken(token)
                                .build());
                if (!page.contents().isEmpty()) {
                    s3Client.deleteObjects(DeleteObjectsRequest.builder()
                            .bucket(bucket)
                            .delete(d -> d.objects(page.contents().stream()
                                    .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                                    .toList()))
                            .build());
                }
                token = page.isTruncated() ? page.nextContinuationToken() : null;
            } while (token != null);
        } catch (SdkException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Cloudflare R2 delete failed",
                    e);
        }
    }

    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
