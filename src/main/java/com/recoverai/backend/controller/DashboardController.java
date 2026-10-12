
package com.recoverai.backend.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.recoverai.backend.repository.RecoveryActionRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;
import com.recoverai.backend.repository.RecoveryCollectionRepository;
import com.recoverai.backend.security.RecoveryCaseAccess;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final RecoveryCaseRepository caseRepository;
    private final RecoveryActionRepository actionRepository;
    private final RecoveryCollectionRepository collectionRepository;
    private final RecoveryCaseAccess access;

    public DashboardController(
            RecoveryCaseRepository caseRepository,
            RecoveryActionRepository actionRepository,
            RecoveryCollectionRepository collectionRepository,
            RecoveryCaseAccess access) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.collectionRepository = collectionRepository;
        this.access = access;
    }

    @GetMapping("/summary")
    public Map<String, Object> getDashboardSummary() {

        Long ownerId = access.isAdmin() ? null : access.currentUserId();
        Object[] caseStats = caseRepository.getDashboardStats(ownerId).get(0);
        Object[] actionStats = actionRepository.getDashboardStats(ownerId).get(0);
        long totalCases = number(caseStats[0]);
        long openCases = number(caseStats[1]);
        long inProgressCases = number(caseStats[2]);
        long recoveredCases = number(caseStats[3]);
        long escalatedCases = number(caseStats[4]);
        long stoppedCases = number(caseStats[5]);
        long exhaustedCases = number(caseStats[6]);
        BigDecimal totalRevenueAtRisk = caseStats[7] == null
                ? BigDecimal.ZERO : new BigDecimal(caseStats[7].toString());

        // Only recorded collection entries count as recovered money.
        BigDecimal totalRecoveredAmount = ownerId == null
                ? collectionRepository.getTotalConfirmedAmount()
                : collectionRepository.getTotalConfirmedAmountByOwnerId(ownerId);

        if (totalRecoveredAmount == null) {
            totalRecoveredAmount = BigDecimal.ZERO;
        }

        long totalActions = number(actionStats[0]);
        long pendingActions = number(actionStats[1]);
        long approvedActions = number(actionStats[2]);
        long executedActions = number(actionStats[3]);
        long successfulActions = number(actionStats[4]);
        long failedActions = number(actionStats[5]);

        // Calculate recovery rate and keep it between 0% and 100%.
        double recoveryRate = 0.0;

        if (totalRevenueAtRisk.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal rate = totalRecoveredAmount
                    .multiply(BigDecimal.valueOf(100))
                    .divide(
                            totalRevenueAtRisk,
                            2,
                            RoundingMode.HALF_UP);

            recoveryRate = rate
                    .max(BigDecimal.ZERO)
                    .min(BigDecimal.valueOf(100))
                    .doubleValue();
        }

        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("totalCases", totalCases);
        dashboard.put("openCases", openCases);
        dashboard.put("inProgressCases", inProgressCases);
        dashboard.put("recoveredCases", recoveredCases);
        dashboard.put("escalatedCases", escalatedCases);
        dashboard.put("stoppedCases", stoppedCases);
        dashboard.put("exhaustedCases", exhaustedCases);

        dashboard.put("totalRevenueAtRisk", totalRevenueAtRisk);
        dashboard.put("totalRecoveredAmount", totalRecoveredAmount);
        dashboard.put("recoveryRate", recoveryRate);

        dashboard.put("totalActions", totalActions);
        dashboard.put("pendingActions", pendingActions);
        dashboard.put("approvedActions", approvedActions);
        dashboard.put("executedActions", executedActions);
        dashboard.put("successfulActions", successfulActions);
        dashboard.put("failedActions", failedActions);

        return dashboard;
    }

    private long number(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }
}
