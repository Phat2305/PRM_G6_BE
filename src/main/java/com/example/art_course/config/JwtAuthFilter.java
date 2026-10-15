package com.example.art_course.config;

import com.example.art_course.common.AppError;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import jakarta.annotation.PostConstruct;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${JWT_SECRET:}")
    private String jwtSecret;

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("FATAL: JWT_SECRET environment variable is missing! Application fails fast to protect security.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FATAL: JWT_SECRET must be at least 256 bits (32 bytes) long for HMAC-SHA algorithms.");
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = Jwts.parser()
                        .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8)))
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();

                // 1. Phân biệt Access Token vs Refresh Token (Chỉ chấp nhận ACCESS)
                String tokenType = claims.get("type", String.class);
                if (tokenType == null || !"ACCESS".equalsIgnoreCase(tokenType)) {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"INVALID_TOKEN_TYPE\",\"message\":\"Chỉ chấp nhận Access Token, không được dùng Refresh Token gọi API\"}}");
                    return;
                }

                // 2. Trích xuất User ID & Role
                String userIdStr = claims.getSubject();
                if (userIdStr == null) {
                    userIdStr = claims.get("userId", String.class);
                }
                if (userIdStr == null) {
                    userIdStr = claims.get("id", String.class);
                }

                String role = claims.get("role", String.class);
                if (role == null) {
                    role = claims.get("roleName", String.class);
                }

                // 3. BR-02, NFR-04: Vai trò CHILD bị chặn tuyệt đối không được gọi API thương mại
                if ("CHILD".equalsIgnoreCase(role)) {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"FORBIDDEN\",\"message\":\"Tài khoản con không có quyền truy cập chức năng này (BR-02, NFR-04)\"}}");
                    return;
                }

                if (userIdStr != null && role != null) {
                    UUID userId = UUID.fromString(userIdStr);
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            userId, null, List.of(authority)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (io.jsonwebtoken.ExpiredJwtException ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"TOKEN_EXPIRED\",\"message\":\"Access token đã hết hạn\"}}");
                return;
            } catch (Exception ex) {
                SecurityContextHolder.clearContext();
                response.setStatus(HttpStatus.UNAUTHORIZED.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"INVALID_TOKEN\",\"message\":\"Access token không hợp lệ hoặc chữ ký không khớp\"}}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
