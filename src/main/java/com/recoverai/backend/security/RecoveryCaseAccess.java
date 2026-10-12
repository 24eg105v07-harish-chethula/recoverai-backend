package com.recoverai.backend.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.repository.AuthUserRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;

@Component
public class RecoveryCaseAccess {
    private final RecoveryCaseRepository cases;
    private final AuthUserRepository users;
    public RecoveryCaseAccess(RecoveryCaseRepository cases, AuthUserRepository users) { this.cases = cases; this.users = users; }
    public boolean isAdmin() { Authentication auth = SecurityContextHolder.getContext().getAuthentication(); return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")); }
    public Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserDetails details)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return users.findByEmail(details.getUsername()).orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED)).getId();
    }
    public RecoveryCase requireCase(Long id) {
        RecoveryCase c = cases.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found"));
        if (!isAdmin() && (c.getOwner() == null || !c.getOwner().getId().equals(currentUserId()))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found");
        return c;
    }
    public void requireAccess(RecoveryCase c) {
        if (!isAdmin() && (c.getOwner() == null || !c.getOwner().getId().equals(currentUserId()))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recovery case not found");
    }
    public boolean canAccess(RecoveryCase c) { return isAdmin() || (c.getOwner() != null && c.getOwner().getId().equals(currentUserId())); }
}
