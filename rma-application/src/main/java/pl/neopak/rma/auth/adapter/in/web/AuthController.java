package pl.neopak.rma.auth.adapter.in.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.neopak.rma.security.JwtTokenProvider;

/**
 * Development/local auth endpoint.
 * Accepts any username + role without password verification.
 * Replace with real user store before going to production.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    public AuthController(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {
        String token = jwtTokenProvider.generateToken(request.username(), request.role());
        return ResponseEntity.ok(new TokenResponse(token));
    }

    public record LoginRequest(String username, String role) {}

    public record TokenResponse(String token) {}
}
