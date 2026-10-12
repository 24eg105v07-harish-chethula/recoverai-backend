
package com.recoverai.backend.entity;

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
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "ai_analyses")
public class AIAnalysis {

    public enum RecommendedAction {
        RETRY_PAYMENT,
        SEND_REMINDER,
        GENERATE_PAYMENT_LINK,
        ESCALATE_TO_HUMAN,
        STOP
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String diagnosis;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_action", nullable = false)
    private RecommendedAction recommendedAction;

    @Column(nullable = false)
    private double confidence;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reasoning;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "input_fingerprint", length = 240)
    private String inputFingerprint;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recovery_case_id", nullable = false, unique = true)
    private RecoveryCase recoveryCase;

    public AIAnalysis() {
    }

    @PrePersist
    protected void onCreate() {
        validateFields();

        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        validateFields();
    }

    private void validateFields() {
        if (diagnosis == null || diagnosis.isBlank()) {
            throw new IllegalStateException(
                    "AI analysis diagnosis is required");
        }

        if (recommendedAction == null) {
            throw new IllegalStateException(
                    "AI analysis recommended action is required");
        }

        if (reasoning == null || reasoning.isBlank()) {
            throw new IllegalStateException(
                    "AI analysis reasoning is required");
        }

        if (!Double.isFinite(confidence)
                || confidence < 0.0
                || confidence > 1.0) {
            throw new IllegalStateException(
                    "Confidence must be between 0.0 and 1.0");
        }

        if (recoveryCase == null) {
            throw new IllegalStateException(
                    "A recovery case is required");
        }
    }

    public Long getId() {
        return id;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public RecommendedAction getRecommendedAction() {
        return recommendedAction;
    }

    public void setRecommendedAction(
            RecommendedAction recommendedAction) {
        this.recommendedAction = recommendedAction;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getReasoning() {
        return reasoning;
    }

    public void setReasoning(String reasoning) {
        this.reasoning = reasoning;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getInputFingerprint() { return inputFingerprint; }
    public void setInputFingerprint(String inputFingerprint) { this.inputFingerprint = inputFingerprint; }

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
