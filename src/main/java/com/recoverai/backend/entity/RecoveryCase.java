
package com.recoverai.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Index;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import com.recoverai.backend.entity.AuthUser;

@Entity
@Table(name = "recovery_cases", indexes = {
        @Index(name = "idx_recovery_cases_owner_status", columnList = "owner_user_id,status")
})
public class RecoveryCase {

    public enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }

    public enum CaseStatus {
        OPEN,
        IN_PROGRESS,
        RECOVERED,
        ESCALATED,
        STOPPED,
        EXHAUSTED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "revenue_at_risk", nullable = false, precision = 12, scale = 2)
    private BigDecimal revenueAtRisk;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel = RiskLevel.LOW;

    @Column(name = "risk_reason", nullable = false)
    private String riskReason = "Risk assessment pending";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CaseStatus status = CaseStatus.OPEN;

    @Column(name = "recovery_attempts", nullable = false)
    private int recoveryAttempts = 0;

    @Column(name = "recovered_amount", precision = 12, scale = 2)
    private BigDecimal recoveredAmount = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    // Nullable for backward compatibility: pre-authentication cases remain unassigned.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_user_id")
    private AuthUser owner;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;

        if (status == null) {
            status = CaseStatus.OPEN;
        }

        if (riskLevel == null) {
            riskLevel = RiskLevel.LOW;
        }

        if (riskReason == null || riskReason.isBlank()) {
            riskReason = "Risk assessment pending";
        }

        if (recoveryAttempts < 0) {
            recoveryAttempts = 0;
        }

        if (recoveredAmount == null) {
            recoveredAmount = BigDecimal.ZERO;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public RecoveryCase() {
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getRevenueAtRisk() {
        return revenueAtRisk;
    }

    public void setRevenueAtRisk(BigDecimal revenueAtRisk) {
        this.revenueAtRisk = revenueAtRisk;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public String getRiskReason() {
        return riskReason;
    }

    public void setRiskReason(String riskReason) {
        this.riskReason = riskReason;
    }

    public CaseStatus getStatus() {
        return status;
    }

    public void setStatus(CaseStatus status) {
        this.status = status;
    }

    public int getRecoveryAttempts() {
        return recoveryAttempts;
    }

    public void setRecoveryAttempts(int recoveryAttempts) {
        if (recoveryAttempts < 0) {
            throw new IllegalArgumentException(
                    "Recovery attempts cannot be negative");
        }

        this.recoveryAttempts = recoveryAttempts;
    }

    public BigDecimal getRecoveredAmount() {
        return recoveredAmount;
    }

    public void setRecoveredAmount(BigDecimal recoveredAmount) {
        if (recoveredAmount != null
                && recoveredAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Recovered amount cannot be negative");
        }

        this.recoveredAmount = recoveredAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public AuthUser getOwner() { return owner; }
    public void setOwner(AuthUser owner) { this.owner = owner; }
}
