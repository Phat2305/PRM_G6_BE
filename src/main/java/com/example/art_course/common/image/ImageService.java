package com.example.art_course.common.image;

import com.example.art_course.common.AppError;
import com.example.art_course.course.entity.Course;
import com.example.art_course.course.repository.CourseRepository;
import com.example.art_course.course.service.CourseReviewPolicy;
import com.example.art_course.supply.entity.Supply;
import com.example.art_course.supply.repository.SupplyRepository;
import com.example.art_course.video.service.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

// Ảnh dụng cụ và ảnh bìa khóa học do giáo viên tải lên (R2 key images/{kind}/{uuid}); ảnh danh mục là công khai.
@Service
@RequiredArgsConstructor
public class ImageService {

    static final String SUPPLIES = "supplies";
    static final String COURSES = "courses";
    private static final String URL_PREFIX = "/api/images/";
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    private final SupplyRepository supplyRepository;
    private final CourseRepository courseRepository;
    private final R2StorageService r2StorageService;

    // Đổi ảnh là đổi thứ người mua nhìn thấy: dụng cụ quay về chờ Staff duyệt (CRS-06/07).
    @Transactional
    public String uploadSupplyImage(UUID supplyId, UUID teacherId, MultipartFile file) {
        Supply supply = supplyRepository.findById(supplyId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "SUPPLY_NOT_FOUND", "Không tìm thấy dụng cụ"));
        if (supply.getTeacherId() == null || !supply.getTeacherId().equals(teacherId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không quản lý dụng cụ này");
        }
        String old = supply.getImageUrl();
        String url = store(SUPPLIES, file);
        supply.setImageUrl(url);
        supply.setStatus("PENDING_REVIEW");
        supplyRepository.save(supply);
        deleteIfOurs(old);
        return url;
    }

    // Đổi ảnh bìa là sửa nội dung khóa: khóa đang công khai quay về chờ Staff duyệt lại.
    @Transactional
    public String uploadCourseCover(UUID courseId, UUID teacherId, MultipartFile file) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new AppError(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Không tìm thấy khóa học"));
        if (course.getTeacherId() == null || !course.getTeacherId().equals(teacherId)) {
            throw new AppError(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không quản lý khóa học này");
        }
        String old = course.getCoverImageUrl();
        String url = store(COURSES, file);
        course.setCoverImageUrl(url);
        CourseReviewPolicy.onContentChanged(course);
        courseRepository.save(course);
        deleteIfOurs(old);
        return url;
    }

    public ResponseInputStream<GetObjectResponse> read(String kind, UUID id) {
        if (!SUPPLIES.equals(kind) && !COURSES.equals(kind)) {
            throw new AppError(HttpStatus.NOT_FOUND, "IMAGE_NOT_FOUND", "Không tìm thấy ảnh");
        }
        return r2StorageService.getObject(key(kind, id));
    }

    private String store(String kind, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_REQUIRED", "Chưa chọn ảnh");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppError(HttpStatus.CONTENT_TOO_LARGE, "IMAGE_TOO_LARGE", "Ảnh tối đa 5 MB");
        }
        String type = file.getContentType();
        if (type == null || !TYPES.contains(type)) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_TYPE", "Chỉ nhận ảnh JPG, PNG hoặc WEBP");
        }
        UUID id = UUID.randomUUID();
        try {
            r2StorageService.uploadBytes(key(kind, id), file.getBytes(), type);
        } catch (IOException e) {
            throw new AppError(HttpStatus.BAD_REQUEST, "IMAGE_READ", "Không đọc được ảnh");
        }
        return URL_PREFIX + kind + "/" + id;
    }

    // Chỉ xóa ảnh do chính hệ thống lưu; URL ngoài (nhập tay trước đây) thì bỏ qua.
    private void deleteIfOurs(String url) {
        if (url == null || !url.startsWith(URL_PREFIX)) {
            return;
        }
        String[] parts = url.substring(URL_PREFIX.length()).split("/");
        if (parts.length != 2) {
            return;
        }
        try {
            r2StorageService.deleteObject(key(parts[0], UUID.fromString(parts[1])));
        } catch (IllegalArgumentException ignored) {
            // không phải ảnh của mình
        }
    }

    private static String key(String kind, UUID id) {
        return "images/" + kind + "/" + id;
    }
}
