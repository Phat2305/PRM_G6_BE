package com.example.art_course.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    // Link cũ /swagger/index.html vẫn dùng được, trỏ về Swagger UI chuẩn của springdoc
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/swagger/index.html", "/swagger-ui/index.html");
    }
}
