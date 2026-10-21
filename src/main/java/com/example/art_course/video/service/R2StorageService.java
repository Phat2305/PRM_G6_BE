
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
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
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
                                AwsBasicCredentials.create(
                                        accessKey,
                                        secretKey)))
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

    @PreDestroy
    public void close() {
        s3Client.close();
    }
}
