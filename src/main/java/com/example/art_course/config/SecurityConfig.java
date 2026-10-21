package com.example.art_course.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.Customizer;
import org.springframework.http.HttpMethod;
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(
                                "/",
                                "/index.html",
                                "/favicon.ico",
                                "/auth/**",
                                "/api/auth/**",
                                "/api/v1/shop/courses/**",
                                "/api/v1/supplies/**",
                                "/api/v1/payments/webhook/**",
                                "/api/v1/payments/return/**",
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger/**",
                                "/swagger-ui.html",
                                "/health"
                        ).permitAll()
                        .requestMatchers("/api/v1/cart/**", "/api/v1/orders/checkout", "/api/v1/orders/my-orders").hasRole("PARENT")
                        .requestMatchers("/api/v1/orders/teacher-orders").hasRole("TEACHER")
                        .requestMatchers("/api/v1/course-reviews/**").hasRole("PARENT")
                        // BR-02, NFR-04: CHILD không được gọi API mua hàng/thanh toán/tiền
                        .requestMatchers(
                                "/api/v1/orders/**",
                                "/api/v1/payments/**",
                                "/api/v1/refunds/**",
                                "/api/refunds/**",
                                "/api/v1/fulfillment/**",
                                "/api/v1/teacher/finance/**"
                        ).access((authentication, context) -> new AuthorizationDecision(
                                authentication.get().isAuthenticated()
                                        && authentication.get().getAuthorities().stream()
                                        .noneMatch(a -> "ROLE_CHILD".equals(a.getAuthority()))))
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
