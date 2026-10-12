package com.recoverai.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.repository.AuthUserRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;

@RestController
@RequestMapping("/api/admin/cases")
public class AdminCaseController {
    private final RecoveryCaseRepository cases;
    private final AuthUserRepository users;
    public AdminCaseController(RecoveryCaseRepository cases, AuthUserRepository users) { this.cases=cases; this.users=users; }
    @GetMapping @Transactional(readOnly = true)
    public java.util.List<CaseOwnerView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        return cases.findAll(PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"))).stream().map(c -> new CaseOwnerView(c.getId(), c.getStatus().name(), c.getPayment().getId(), c.getOwner() == null ? null : c.getOwner().getId(), c.getOwner() == null ? null : c.getOwner().getEmail())).toList();
    }
    @PatchMapping("/{caseId}/owner/{userId}") @Transactional
    public OwnerResponse assign(@PathVariable Long caseId, @PathVariable Long userId) {
        RecoveryCase c=cases.findById(caseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found"));
        AuthUser u=users.findById(userId).filter(AuthUser::isEnabled).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user not found"));
        c.setOwner(u); cases.save(c); return new OwnerResponse(caseId, u.getId(), u.getEmail());
    }
    @DeleteMapping("/{caseId}/owner") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void unassign(@PathVariable Long caseId) {
        RecoveryCase c=cases.findById(caseId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found"));
        c.setOwner(null); cases.save(c);
    }
    public record OwnerResponse(Long caseId, Long ownerUserId, String ownerEmail) { }
    public record CaseOwnerView(Long caseId, String status, Long paymentId, Long ownerUserId, String ownerEmail) { }
}
