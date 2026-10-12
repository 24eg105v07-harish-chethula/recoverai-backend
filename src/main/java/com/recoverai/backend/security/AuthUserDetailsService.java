package com.recoverai.backend.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import com.recoverai.backend.repository.AuthUserRepository;

@Service
public class AuthUserDetailsService implements UserDetailsService {
    private final AuthUserRepository users;
    public AuthUserDetailsService(AuthUserRepository users) { this.users = users; }
    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        var user = users.findByEmail(email).orElseThrow(() -> new UsernameNotFoundException("Invalid credentials"));
        return User.withUsername(user.getEmail()).password(user.getPasswordHash()).disabled(!user.isEnabled()).roles(user.getRole().name()).build();
    }
}
