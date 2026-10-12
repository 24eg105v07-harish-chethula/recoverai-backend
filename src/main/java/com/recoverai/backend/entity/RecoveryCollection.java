
package com.recoverai.backend.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.*;

@Entity
@Table(
    name = "recovery_collections",
    uniqueConstraints = @UniqueConstraint(
        columnNames = "reference_code"
    )
)
public class RecoveryCollection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id", nullable = false)
    private RecoveryCase recoveryCase;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "reference_code", nullable = false, unique = true)
    private String referenceCode;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(columnDefinition = "TEXT")
    private String note;

    public RecoveryCollection() {
    }

    @PrePersist
    public void prePersist() {
        validateCollection();

        if (confirmedAt == null) {
            confirmedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        validateCollection();
    }

    private void validateCollection() {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalStateException(
                    "Collection amount must be greater than zero");
        }

        if (referenceCode == null || referenceCode.isBlank()) {
            throw new IllegalStateException(
                    "Reference code is required");
        }

        referenceCode = referenceCode.trim();

        if (referenceCode.isBlank()) {
            throw new IllegalStateException(
                    "Reference code cannot be blank");
        }
    }

    public Long getId() {
        return id;
    }

    public RecoveryCase getRecoveryCase() {
        return recoveryCase;
    }

    public void setRecoveryCase(RecoveryCase recoveryCase) {
        this.recoveryCase = recoveryCase;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getReferenceCode() {
        return referenceCode;
    }

    public void setReferenceCode(String referenceCode) {
        this.referenceCode = referenceCode;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }
}
