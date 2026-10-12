
package com.recoverai.backend.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.recoverai.backend.entity.AIAnalysis;
import com.recoverai.backend.service.AIRecommendationService;
import com.recoverai.backend.security.RecoveryCaseAccess;

@RestController
@RequestMapping("/api/ai-analysis")
public class AIAnalysisController {

    private final AIRecommendationService recommendationService;
    private final RecoveryCaseAccess access;

    public AIAnalysisController(
            AIRecommendationService recommendationService,
            RecoveryCaseAccess access) {
        this.recommendationService = recommendationService;
        this.access = access;
    }

    @PostMapping("/case/{caseId}")
    public ResponseEntity<Map<String, Object>> analyzeCase(
            @PathVariable Long caseId) {

        if (caseId == null || caseId <= 0) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Case ID must be a positive number");

            return ResponseEntity.badRequest().body(error);
        }

        try {
            access.requireCase(caseId);
            AIAnalysis analysis =
                    recommendationService.analyzeCase(caseId);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("id", analysis.getId());
            response.put("caseId", analysis.getRecoveryCase().getId());
            response.put("diagnosis", analysis.getDiagnosis());
            response.put(
                    "recommendedAction",
                    analysis.getRecommendedAction().name());
            response.put("confidence", analysis.getConfidence());
            response.put("reasoning", analysis.getReasoning());
            response.put("modelName", analysis.getModelName());
            response.put("createdAt", analysis.getCreatedAt());

            return ResponseEntity.ok(response);

        } catch (org.springframework.web.server.ResponseStatusException ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", ex.getReason() != null
                    ? ex.getReason()
                    : "Unable to analyse the recovery case");

            return ResponseEntity.status(ex.getStatusCode()).body(error);

        } catch (Exception ex) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "Unable to analyse the recovery case");

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(error);
        }
    }
}
