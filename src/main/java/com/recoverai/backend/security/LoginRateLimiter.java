package com.recoverai.backend.security;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class LoginRateLimiter {
    private static final int LIMIT = 10;
    private static final long WINDOW_SECONDS = 900;
    private final ConcurrentHashMap<String, Window> attempts = new ConcurrentHashMap<>();
    public void check(String address) {
        long now = Instant.now().getEpochSecond();
        Window window = attempts.compute(address, (key, old) -> old == null || now - old.startedAt >= WINDOW_SECONDS ? new Window(now, 0) : old);
        if (window.count.incrementAndGet() > LIMIT) {
            window.count.decrementAndGet();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many login attempts. Please try again later.");
        }
    }
    public void clear(String address) { attempts.remove(address); }
    private static final class Window { final long startedAt; final AtomicInteger count; Window(long startedAt, int count) { this.startedAt=startedAt; this.count=new AtomicInteger(count); } }
}
