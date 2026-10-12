package com.recoverai.backend.controller;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.repository.AuthUserRepository;
import com.recoverai.backend.repository.PaymentRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AuthUserRepository users;
    private final PaymentRepository payments;
    private final RecoveryCaseRepository cases;
    public AdminUserController(AuthUserRepository users, PaymentRepository payments, RecoveryCaseRepository cases) {
        this.users = users;
        this.payments = payments;
        this.cases = cases;
    }
    @GetMapping public List<AdminUserView> list(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "50") int size,
                                                @RequestParam(defaultValue = "") String search) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        var pageable = PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
        String query = search == null ? "" : search.trim();
        var results = query.isEmpty() ? users.findAll(pageable)
                : users.findByFullNameContainingIgnoreCaseOrEmailContainingIgnoreCase(query, query, pageable);
        return results.map(this::view).getContent();
    }
    @GetMapping("/{id}") public AdminUserView get(@PathVariable Long id) {
        AuthUser target = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return view(target);
    }
    @PatchMapping("/{id}/status") @Transactional
    public AdminUserView status(@PathVariable Long id, @RequestBody StatusRequest request, @AuthenticationPrincipal UserDetails principal) {
        AuthUser target = users.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (target.getEmail().equals(principal.getUsername()) && !request.enabled) throw new ResponseStatusException(HttpStatus.CONFLICT, "An administrator cannot disable their own account");
        if (!request.enabled && target.getRole() == AuthUser.Role.ADMIN && users.countByRoleAndEnabledTrue(AuthUser.Role.ADMIN) <= 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "The last active administrator cannot be disabled");
        target.setEnabled(request.enabled); return view(users.save(target));
    }
    private AdminUserView view(AuthUser u) {
        return new AdminUserView(u.getId(), u.getFullName(), u.getEmail(), u.getPhoneNumber(), u.getRole().name(),
                u.isEnabled(), u.getCreatedAt(), payments.countByOwnerId(u.getId()), cases.countByOwnerId(u.getId()));
    }
    public record AdminUserView(Long id, String fullName, String email, String phoneNumber, String role,
                                boolean enabled, java.time.LocalDateTime createdAt,
                                long assignedPaymentCount, long assignedCaseCount) { }
    public record StatusRequest(boolean enabled) { }
}
