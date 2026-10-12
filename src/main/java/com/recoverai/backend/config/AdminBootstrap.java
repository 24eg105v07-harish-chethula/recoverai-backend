package com.recoverai.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.repository.AuthUserRepository;

@Component
public class AdminBootstrap implements CommandLineRunner {
    private final AuthUserRepository users;
    private final PasswordEncoder encoder;
    private final String email;
    private final String password;
    private final String name;
    public AdminBootstrap(AuthUserRepository users, PasswordEncoder encoder, @Value("${app.bootstrap-admin.email:}") String email, @Value("${app.bootstrap-admin.password:}") String password, @Value("${app.bootstrap-admin.name:RecoverAI Administrator}") String name) { this.users=users; this.encoder=encoder; this.email=email; this.password=password; this.name=name; }
    @Override @Transactional public void run(String... args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.isBlank() || password.length() < 16) throw new IllegalStateException("Set both BOOTSTRAP_ADMIN_EMAIL and a BOOTSTRAP_ADMIN_PASSWORD of at least 16 characters");
        String normalized = email.trim().toLowerCase(java.util.Locale.ROOT);
        if (users.existsByEmail(normalized)) return;
        AuthUser admin = new AuthUser(); admin.setFullName(name); admin.setEmail(normalized); admin.setPasswordHash(encoder.encode(password)); admin.setRole(AuthUser.Role.ADMIN); admin.setEnabled(true); users.save(admin);
    }
}
