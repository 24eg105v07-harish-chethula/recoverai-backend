package com.recoverai.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.entity.Payment;
import com.recoverai.backend.repository.AuthUserRepository;
import com.recoverai.backend.repository.PaymentRepository;

@RestController
@RequestMapping("/api/admin/payments")
public class AdminPaymentController {
    private final PaymentRepository payments;
    private final AuthUserRepository users;
    public AdminPaymentController(PaymentRepository payments, AuthUserRepository users) { this.payments=payments; this.users=users; }
    @GetMapping @Transactional(readOnly = true)
    public List<PaymentOwnerView> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "50") int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        return payments.findAll(PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"))).stream().map(p -> new PaymentOwnerView(p.getId(), p.getAmount(), p.getStatus().name(), p.getCustomer().getName(), p.getOwner() == null ? null : p.getOwner().getId(), p.getOwner() == null ? null : p.getOwner().getEmail())).toList();
    }
    @PatchMapping("/{paymentId}/owner/{userId}") @Transactional
    public OwnerResponse assign(@PathVariable Long paymentId, @PathVariable Long userId) {
        Payment payment=payments.findById(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        if (payment.getStatus() != Payment.PaymentStatus.FAILED) throw new ResponseStatusException(HttpStatus.CONFLICT, "Only failed payments can be assigned for recovery");
        AuthUser user=users.findById(userId).filter(AuthUser::isEnabled).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active user not found"));
        payment.setOwner(user); payments.save(payment); return new OwnerResponse(paymentId, user.getId(), user.getEmail());
    }
    @DeleteMapping("/{paymentId}/owner") @ResponseStatus(HttpStatus.NO_CONTENT) @Transactional
    public void unassign(@PathVariable Long paymentId) {
        Payment payment=payments.findById(paymentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found"));
        payment.setOwner(null); payments.save(payment);
    }
    public record OwnerResponse(Long paymentId, Long ownerUserId, String ownerEmail) { }
    public record PaymentOwnerView(Long paymentId, java.math.BigDecimal amount, String status, String customerName, Long ownerUserId, String ownerEmail) { }
}
