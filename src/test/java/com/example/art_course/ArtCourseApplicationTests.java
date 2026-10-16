package com.example.art_course;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Vô hiệu hóa contextLoads để không tự khởi động ApplicationContext kết nối database khi chưa có môi trường DB dev/Testcontainers")
@SpringBootTest
class ArtCourseApplicationTests {

    @Test
    void contextLoads() {
    }

}
