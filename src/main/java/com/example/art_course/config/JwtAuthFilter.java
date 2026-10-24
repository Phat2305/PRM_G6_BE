package com.example.art_course.config;
import org.springframework.web.cors.CorsUtils;
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
import com.example.art_course.user.repository.UserRepository;

import java.io.IOException;
import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Value("${JWT_SECRET:${jwt.secret:FALL2026_ART_COURSE_PRM391_GROUP_6}}")
    private String jwtSecret;

    private final UserRepository userRepository;

    public JwtAuthFilter(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.trim().isEmpty()) {
            throw new IllegalStateException("FATAL: JWT_SECRET environment variable is missing! Application fails fast to protect security.");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FATAL: JWT_SECRET must be at least 256 bits (32 bytes) long for HMAC-SHA algorithms.");
        }
    }

    // Token cũ/hết hạn không được chặn đăng nhập lại
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
         return CorsUtils.isPreFlightRequest(request)
            || path.startsWith("/auth/")
            || path.startsWith("/api/auth/");
    }

    private void reject(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        SecurityContextHolder.clearContext();
        writeError(response, status, code, message);
    }

    // Cùng dạng ApiResponse.error; message là hằng số nên ghép chuỗi không cần escape
    public static void writeError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"error\":{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}}");
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

                // 1. Phân biệt Access Token vs Refresh Token (Chỉ chấp nhận ACCESS, từ chối REFRESH)
                String tokenType = claims.get("type", String.class);
                if (tokenType != null && !"ACCESS".equalsIgnoreCase(tokenType)) {
                    reject(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN_TYPE",
                            "Chỉ chấp nhận Access Token, không được dùng Refresh Token gọi API");
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

                // 3. BR-02, NFR-04: CHILD bị chặn ở API thương mại trong SecurityConfig, không chặn ở đây
                if (userIdStr != null && role != null) {
                    Object principal;
                    try {
                        principal = UUID.fromString(userIdStr);
                    } catch (IllegalArgumentException e) {
                        principal = userIdStr;
                    }
                    // Khóa/xóa tài khoản và thu hồi token có hiệu lực ngay, không đợi token hết hạn
                    if (principal instanceof UUID uid) {
                        var state = userRepository.findAuthStateById(uid);
                        if (state.isEmpty() || !"ACTIVE".equalsIgnoreCase(state.get().getStatus())) {
                            reject(response, HttpStatus.FORBIDDEN, "ACCOUNT_NOT_ACTIVE",
                                    "Tài khoản đã bị khóa hoặc không còn tồn tại");
                            return;
                        }
                        Instant validAfter = state.get().getTokensValidAfter();
                        // iat tính theo giây nên so theo giây; token không có iat thì không chứng minh được là mới
                        if (validAfter != null && (claims.getIssuedAt() == null
                                || claims.getIssuedAt().toInstant().getEpochSecond() <= validAfter.getEpochSecond())) {
                            reject(response, HttpStatus.UNAUTHORIZED, "TOKEN_REVOKED",
                                    "Phiên đăng nhập đã bị thu hồi, vui lòng đăng nhập lại");
                            return;
                        }
                    }
                    SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role.toUpperCase());
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            principal, null, List.of(authority)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            } catch (io.jsonwebtoken.ExpiredJwtException ex) {
                reject(response, HttpStatus.UNAUTHORIZED, "TOKEN_EXPIRED", "Access token đã hết hạn");
                return;
            } catch (Exception ex) {
                reject(response, HttpStatus.UNAUTHORIZED, "INVALID_TOKEN",
                        "Access token không hợp lệ hoặc chữ ký không khớp");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
