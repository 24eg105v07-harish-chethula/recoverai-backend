
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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "recovery_actions")
public class RecoveryAction {

    public enum ActionType {
        RETRY_PAYMENT,
        SEND_REMINDER,
        GENERATE_PAYMENT_LINK,
        ESCALATE_TO_HUMAN,
        STOP
    }

    public enum ActionStatus {
        PENDING,
        APPROVED,
        EXECUTED,
        SUCCESS,
        FAILED,
        BLOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActionStatus status;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "amount_recovered", precision = 12, scale = 2)
    private BigDecimal amountRecovered = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String result;

    @Column(name = "executed_at")
    private LocalDateTime executedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }

        if (status == null) {
            status = ActionStatus.PENDING;
        }

        if (amountRecovered == null) {
            amountRecovered = BigDecimal.ZERO;
        }

        if (attemptNumber < 0) {
            throw new IllegalStateException(
                    "Attempt number cannot be negative");
        }

        validateAmountRecovered();
    }

    @PreUpdate
    protected void onUpdate() {
        validateAmountRecovered();

        if (attemptNumber < 0) {
            throw new IllegalStateException(
                    "Attempt number cannot be negative");
        }
    }

    private void validateAmountRecovered() {
        if (amountRecovered != null
                && amountRecovered.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalStateException(
                    "Recovered amount cannot be negative");
        }
    }

    public RecoveryAction() {
    }

    public Long getId() {
        return id;
    }

    public ActionType getActionType() {
        return actionType;
    }

    public void setActionType(ActionType actionType) {
        this.actionType = actionType;
    }

    public ActionStatus getStatus() {
        return status;
    }

    public void setStatus(ActionStatus status) {
        this.status = status;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(int attemptNumber) {
        if (attemptNumber < 0) {
            throw new IllegalArgumentException(
                    "Attempt number cannot be negative");
        }

        this.attemptNumber = attemptNumber;
    }

    public BigDecimal getAmountRecovered() {
        return amountRecovered;
    }

    public void setAmountRecovered(BigDecimal amountRecovered) {
        if (amountRecovered != null
                && amountRecovered.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException(
                    "Recovered amount cannot be negative");
        }

        this.amountRecovered = amountRecovered;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public void setExecutedAt(LocalDateTime executedAt) {
        this.executedAt = executedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public RecoveryCase getRecoveryCase() {
        return recoveryCase;
    }

    public void setRecoveryCase(RecoveryCase recoveryCase) {
        this.recoveryCase = recoveryCase;
    }
}
