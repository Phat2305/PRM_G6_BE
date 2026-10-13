package auth.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;
@Getter
@Setter
public class AuthResponse {
    private String token;
    private UUID id;
    private String username;
    private String role;
    private String status;
}
