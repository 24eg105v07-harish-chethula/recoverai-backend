package com.recoverai.backend.controller;

import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.repository.AuthUserRepository;
import com.recoverai.backend.security.JwtService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final Pattern PASSWORD = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{12,72}$");
    private final AuthUserRepository users;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final com.recoverai.backend.security.LoginRateLimiter rateLimiter;
    public AuthController(AuthUserRepository users, PasswordEncoder encoder, AuthenticationManager authenticationManager, JwtService jwtService, com.recoverai.backend.security.LoginRateLimiter rateLimiter) { this.users = users; this.encoder = encoder; this.authenticationManager = authenticationManager; this.jwtService = jwtService; this.rateLimiter = rateLimiter; }

    @PostMapping("/signup") @Transactional
    public AuthResponse signup(@Valid @RequestBody SignupRequest request) {
        String email = request.email.trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmail(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        if (!PASSWORD.matcher(request.password).matches()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be 12–72 characters and include uppercase, lowercase, number, and symbol");
        if (request.password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must not exceed 72 UTF-8 bytes");
        AuthUser user = new AuthUser(); user.setFullName(request.fullName.trim()); user.setEmail(email);
        user.setPhoneNumber(request.phoneNumber == null || request.phoneNumber.isBlank() ? null : request.phoneNumber.trim());
        user.setPasswordHash(encoder.encode(request.password)); user.setRole(AuthUser.Role.USER); user.setEnabled(true);
        users.save(user);
        return response(user);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String remoteAddress = servletRequest.getRemoteAddr();
        rateLimiter.check(remoteAddress);
        String email = request.email.trim().toLowerCase(java.util.Locale.ROOT);
        try { authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.password)); rateLimiter.clear(remoteAddress); }
        catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"); }
        AuthUser user = users.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password"));
        if (!user.isEnabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "This account is disabled");
        return response(user);
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal UserDetails principal) {
        AuthUser user = users.findByEmail(principal.getUsername()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return view(user);
    }

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() { /* Bearer JWT is held only in frontend session memory and discarded on logout. */ }

    private AuthResponse response(AuthUser user) { return new AuthResponse(jwtService.issue(user), view(user)); }
    private UserView view(AuthUser u) { return new UserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole().name(), u.isEnabled(), u.getCreatedAt()); }

    public record SignupRequest(@NotBlank @Size(max=120) String fullName, @NotBlank @Email @Size(max=254) String email, @Size(max=32) String phoneNumber, @NotBlank String password) { }
    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) { }
    public record UserView(Long id, String fullName, String email, String phoneNumber, String role, boolean enabled, java.time.LocalDateTime createdAt) { }
    public record AuthResponse(String token, UserView user) { }
}
