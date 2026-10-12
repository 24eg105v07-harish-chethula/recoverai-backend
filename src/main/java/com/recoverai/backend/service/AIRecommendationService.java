
package com.recoverai.backend.service;

import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.recoverai.backend.entity.AIAnalysis;
import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.repository.AIAnalysisRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;

@Service
public class AIRecommendationService {

    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    private final AIAnalysisRepository analysisRepository;
    private final RecoveryCaseRepository caseRepository;

    public AIRecommendationService(
            AIAnalysisRepository analysisRepository,
            RecoveryCaseRepository caseRepository) {
        this.analysisRepository = analysisRepository;
        this.caseRepository = caseRepository;
    }

    @Transactional
    public AIAnalysis analyzeCase(Long caseId) {
        RecoveryCase recoveryCase = caseRepository.findById(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recovery case not found: " + caseId));

        BigDecimal riskAmount = recoveryCase.getRevenueAtRisk();

        if (riskAmount == null || riskAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recovery case has an invalid amount at risk");
        }

        BigDecimal recoveredAmount = recoveryCase.getRecoveredAmount() == null
                ? BigDecimal.ZERO
                : recoveryCase.getRecoveredAmount();

        if (recoveredAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Recovered amount cannot be negative");
        }

        BigDecimal outstanding = riskAmount
                .subtract(recoveredAmount)
                .max(BigDecimal.ZERO);

        int attempts = recoveryCase.getRecoveryAttempts();
        RecoveryCase.RiskLevel risk = recoveryCase.getRiskLevel();
        String inputFingerprint = String.join("|",
                recoveryCase.getStatus().name(),
                risk == null ? "" : risk.name(),
                riskAmount.toPlainString(),
                recoveredAmount.toPlainString(),
                Integer.toString(attempts));
        AIAnalysis analysis = analysisRepository.findByRecoveryCaseId(caseId)
                .orElseGet(AIAnalysis::new);
        if (inputFingerprint.equals(analysis.getInputFingerprint())) {
            return analysis;
        }

        AIAnalysis.RecommendedAction action;
        String diagnosis;
        String reasoning;
        double confidence;

        switch (recoveryCase.getStatus()) {
            case RECOVERED -> {
                action = AIAnalysis.RecommendedAction.STOP;
                diagnosis = "Recovery completed";
                reasoning = "The case is marked recovered. No further recovery actions should be initiated.";
                confidence = 0.99;
            }

            case STOPPED -> {
                action = AIAnalysis.RecommendedAction.STOP;
                diagnosis = "Recovery stopped";
                reasoning = "The case is stopped. Human review is required before any restart.";
                confidence = 0.99;
            }

            case ESCALATED -> {
                action = AIAnalysis.RecommendedAction.ESCALATE_TO_HUMAN;
                diagnosis = "Human review required";
                reasoning = "The case has been escalated. Further recovery actions require human review.";
                confidence = 0.95;
            }

            case EXHAUSTED -> {
                action = AIAnalysis.RecommendedAction.ESCALATE_TO_HUMAN;
                diagnosis = "Recovery attempts exhausted";
                reasoning = "The case has exhausted all allowed recovery attempts. Human review is required before any further action.";
                confidence = 0.99;
            }

            default -> {
                if (outstanding.compareTo(BigDecimal.ZERO) == 0) {
                    action = AIAnalysis.RecommendedAction.STOP;
                    diagnosis = "No outstanding amount";
                    reasoning = "No outstanding balance is calculated. Verify the payment and confirmed collection records before closing the case.";
                    confidence = 0.95;

                } else if (attempts >= MAX_RECOVERY_ATTEMPTS) {
                    action = AIAnalysis.RecommendedAction.ESCALATE_TO_HUMAN;
                    diagnosis = "Recovery attempt limit reached";
                    reasoning = "Three recovery attempts have been recorded. Stop automated recovery and request human review.";
                    confidence = 0.95;

                } else if (risk == null) {
                    action = AIAnalysis.RecommendedAction.ESCALATE_TO_HUMAN;
                    diagnosis = "Risk assessment missing";
                    reasoning = "The case has no risk level. Human review is required before choosing a recovery action.";
                    confidence = 0.90;

                } else if (risk == RecoveryCase.RiskLevel.CRITICAL) {
                    action = AIAnalysis.RecommendedAction.ESCALATE_TO_HUMAN;
                    diagnosis = "Critical-risk case";
                    reasoning = "The case is classified as critical risk. Human review is required before further recovery actions.";
                    confidence = 0.90;

                } else if (risk == RecoveryCase.RiskLevel.HIGH
                        || risk == RecoveryCase.RiskLevel.MEDIUM) {
                    action = AIAnalysis.RecommendedAction.SEND_REMINDER;
                    diagnosis = "Payment follow-up recommended";
                    reasoning = "The case has " + risk + " risk and "
                            + attempts
                            + " recorded attempts. Consider a polite payment reminder after approval.";
                    confidence = 0.75;

                } else {
                    action = AIAnalysis.RecommendedAction.RETRY_PAYMENT;
                    diagnosis = "Retry may be appropriate";
                    reasoning = "The case has low risk and "
                            + attempts
                            + " recorded attempts. Verify payment status and obtain approval before retrying.";
                    confidence = 0.70;
                }
            }
        }

        analysis.setRecoveryCase(recoveryCase);
        analysis.setRecommendedAction(action);
        analysis.setDiagnosis(diagnosis);
        analysis.setReasoning(reasoning);
        analysis.setConfidence(confidence);
        analysis.setModelName("RecoverAI-Rules-v1");
        analysis.setInputFingerprint(inputFingerprint);

        return analysisRepository.save(analysis);
    }
}
