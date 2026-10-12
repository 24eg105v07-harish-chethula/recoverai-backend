package com.recoverai.backend.controller;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import com.recoverai.backend.security.RecoveryCaseAccess;

import com.recoverai.backend.dto.PaymentResponse;
import com.recoverai.backend.entity.Payment;
import com.recoverai.backend.service.PaymentService;
import com.recoverai.backend.repository.PaymentRepository;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository payments;
    private final RecoveryCaseAccess access;

    public PaymentController(PaymentService paymentService, PaymentRepository payments, RecoveryCaseAccess access) {
        this.paymentService = paymentService;
        this.payments = payments;
        this.access = access;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(
            @RequestParam Long customerId,
            @RequestBody Payment payment) {

        return paymentService.createPayment(customerId, payment);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentResponse> getPayments() {
        return paymentService.getPayments();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return paymentService.getPayment(id);
    }

    @GetMapping("/failed")
    @PreAuthorize("hasRole('ADMIN')")
    public List<PaymentResponse> getFailedPayments() {
        return paymentService.getFailedPayments();
    }

    @GetMapping("/owned-failed")
    @PreAuthorize("hasRole('USER')")
    @Transactional(readOnly = true)
    public List<PaymentResponse> getOwnedFailedPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0) throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        return payments.findByOwnerIdAndStatus(access.currentUserId(), Payment.PaymentStatus.FAILED,
                PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"))).stream()
                .map(payment -> new PaymentResponse(payment.getId(), payment.getAmount(), payment.getRazorpayOrderId(), payment.getRazorpayPaymentId(), payment.getStatus(), payment.getFailureReason(), payment.getCreatedAt(), payment.getCustomer().getId(), payment.getCustomer().getName(), payment.getCustomer().getEmail(), payment.getCustomer().getPhone())).toList();
    }
}
