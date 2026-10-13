
package com.recoverai.backend.controller;

import com.recoverai.backend.entity.AuthUser;
import com.recoverai.backend.entity.Payment;
import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.repository.PaymentRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;
import com.recoverai.backend.repository.RecoveryCollectionRepository;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.recoverai.backend.security.RecoveryCaseAccess;
import com.recoverai.backend.repository.AuthUserRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/recovery-cases")
public class RecoveryCaseController {

    private final RecoveryCaseRepository recoveryCaseRepository;
    private final PaymentRepository paymentRepository;
    private final RecoveryCollectionRepository collectionRepository;
    private final RecoveryCaseAccess access;
    private final AuthUserRepository users;

    public RecoveryCaseController(
            RecoveryCaseRepository recoveryCaseRepository,
            PaymentRepository paymentRepository,
            RecoveryCollectionRepository collectionRepository,
            RecoveryCaseAccess access,
            AuthUserRepository users) {
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.paymentRepository = paymentRepository;
        this.collectionRepository = collectionRepository;
        this.access = access;
        this.users = users;
    }

    @PostMapping("/from-payment/{paymentId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RecoveryCaseResponse createFromPayment(
            @PathVariable Long paymentId) {

        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found: " + paymentId));

        if (payment.getStatus() != Payment.PaymentStatus.FAILED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Recovery case can only be created for failed payments");
        }
        if (!access.isAdmin() && (payment.getOwner() == null || !payment.getOwner().getId().equals(access.currentUserId()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found");
        }

        if (recoveryCaseRepository.findByPaymentId(paymentId).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recovery case already exists for payment: " + paymentId);
        }

        RecoveryCase recoveryCase = new RecoveryCase();
        recoveryCase.setPayment(payment);
        AuthUser owner = (payment.getOwner() != null) ? payment.getOwner() : users.findById(access.currentUserId()).orElseThrow();
        recoveryCase.setOwner(owner);
        recoveryCase.setRevenueAtRisk(payment.getAmount());
        recoveryCase.setRiskLevel(calculateRiskLevel(payment.getAmount()));
        recoveryCase.setRiskReason(
                payment.getFailureReason() == null
                        || payment.getFailureReason().isBlank()
                        ? "Payment failed; reason not provided"
                        : payment.getFailureReason());
        recoveryCase.setStatus(RecoveryCase.CaseStatus.OPEN);
        recoveryCase.setRecoveryAttempts(0);
        recoveryCase.setRecoveredAmount(BigDecimal.ZERO);

        return toResponse(recoveryCaseRepository.save(recoveryCase));
    }

    @GetMapping
    public List<RecoveryCaseResponse> getRecoveryCases(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = pageRequest(page, size, "createdAt");
        Page<RecoveryCase> visible = access.isAdmin()
                ? recoveryCaseRepository.findAll(pageable)
                : recoveryCaseRepository.findAllByOwnerId(access.currentUserId(), pageable);
        return visible.map(this::toResponse).getContent();
    }

    private Pageable pageRequest(int page, int size, String sort) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        return PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, sort));
    }

    @GetMapping("/{id}")
    public RecoveryCaseResponse getRecoveryCase(@PathVariable Long id) {
        RecoveryCase recoveryCase = access.requireCase(id);

        return toResponse(recoveryCase);
    }

    /*
     * Synchronize recovered amounts from confirmed collection records.
     * Do not automatically reopen stopped or escalated cases.
     */
    @PutMapping("/{id}/repair")
    @Transactional
    public RecoveryCaseResponse repairRecoveryCase(@PathVariable Long id) {
        RecoveryCase recoveryCase = access.requireCase(id);

        BigDecimal confirmedAmount =
                collectionRepository.getConfirmedAmountByCaseId(id);

        if (confirmedAmount == null) {
            confirmedAmount = BigDecimal.ZERO;
        }

        if (confirmedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Confirmed collection amount cannot be negative");
        }

        recoveryCase.setRecoveredAmount(confirmedAmount);

        if (recoveryCase.getStatus() == RecoveryCase.CaseStatus.STOPPED
                || recoveryCase.getStatus() == RecoveryCase.CaseStatus.ESCALATED
                || recoveryCase.getStatus() == RecoveryCase.CaseStatus.EXHAUSTED) {

            // Preserve the existing status. Human review is required
            // before a stopped or escalated case can resume.
            return toResponse(recoveryCaseRepository.save(recoveryCase));
        }

        BigDecimal risk = recoveryCase.getRevenueAtRisk();

        if (confirmedAmount.compareTo(risk) >= 0) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.RECOVERED);
        } else if (confirmedAmount.compareTo(BigDecimal.ZERO) > 0) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.IN_PROGRESS);
        } else {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.OPEN);
        }

        return toResponse(recoveryCaseRepository.save(recoveryCase));
    }

    private RecoveryCaseResponse toResponse(RecoveryCase recoveryCase) {
        RecoveryCaseResponse response = new RecoveryCaseResponse();

        response.id = recoveryCase.getId();
        response.revenueAtRisk = recoveryCase.getRevenueAtRisk();
        response.riskLevel = recoveryCase.getRiskLevel() != null
                ? recoveryCase.getRiskLevel().name()
                : null;
        response.riskReason = recoveryCase.getRiskReason();
        response.status = recoveryCase.getStatus() != null
                ? recoveryCase.getStatus().name()
                : null;
        response.recoveryAttempts = recoveryCase.getRecoveryAttempts();
        response.recoveredAmount = recoveryCase.getRecoveredAmount();
        response.createdAt = recoveryCase.getCreatedAt();
        response.updatedAt = recoveryCase.getUpdatedAt();

        if (recoveryCase.getPayment() != null) {
            response.paymentId = recoveryCase.getPayment().getId();
        }

        return response;
    }

    private RecoveryCase.RiskLevel calculateRiskLevel(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            return RecoveryCase.RiskLevel.LOW;
        }

        if (amount.compareTo(new BigDecimal("10000")) >= 0) {
            return RecoveryCase.RiskLevel.CRITICAL;
        }

        if (amount.compareTo(new BigDecimal("5000")) >= 0) {
            return RecoveryCase.RiskLevel.HIGH;
        }

        if (amount.compareTo(new BigDecimal("1000")) >= 0) {
            return RecoveryCase.RiskLevel.MEDIUM;
        }

        return RecoveryCase.RiskLevel.LOW;
    }

    public static class RecoveryCaseResponse {
        public Long id;
        public BigDecimal revenueAtRisk;
        public String riskLevel;
        public String riskReason;
        public String status;
        public int recoveryAttempts;
        public BigDecimal recoveredAmount;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
        public Long paymentId;
    }
}
