
package com.recoverai.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.entity.RecoveryCollection;
import com.recoverai.backend.repository.RecoveryCaseRepository;
import com.recoverai.backend.repository.RecoveryCollectionRepository;
import com.recoverai.backend.security.RecoveryCaseAccess;

@RestController
@RequestMapping("/api/recovery-collections")
public class RecoveryCollectionController {

    private final RecoveryCollectionRepository collectionRepository;
    private final RecoveryCaseRepository caseRepository;
    private final RecoveryCaseAccess access;

    public RecoveryCollectionController(
            RecoveryCollectionRepository collectionRepository,
            RecoveryCaseRepository caseRepository,
            RecoveryCaseAccess access) {
        this.collectionRepository = collectionRepository;
        this.caseRepository = caseRepository;
        this.access = access;
    }

    @PostMapping("/confirm")
    @Transactional
    public Map<String, Object> confirmCollection(
            @RequestParam Long caseId,
            @RequestParam BigDecimal amount,
            @RequestParam String referenceCode,
            @RequestParam(required = false) String note) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be positive");
        }

        if (referenceCode == null || referenceCode.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Reference code is required");
        }

        RecoveryCase recoveryCase = caseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recovery case not found: " + caseId));
        access.requireAccess(recoveryCase);

        RecoveryCase.CaseStatus status = recoveryCase.getStatus();

        if (status == RecoveryCase.CaseStatus.STOPPED
                || status == RecoveryCase.CaseStatus.ESCALATED
                || status == RecoveryCase.CaseStatus.EXHAUSTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case is stopped, escalated, or exhausted. Human review is required before recording a collection.");
        }

        if (status == RecoveryCase.CaseStatus.RECOVERED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case is already recovered.");
        }

        String reference = referenceCode.trim();

        if (collectionRepository.existsByReferenceCode(reference)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Reference code already exists");
        }

        BigDecimal risk = recoveryCase.getRevenueAtRisk();

        if (risk == null || risk.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The case has no valid amount at risk");
        }

        BigDecimal recovered =
                collectionRepository.getConfirmedAmountByCaseId(caseId);

        if (recovered == null) {
            recovered = BigDecimal.ZERO;
        }

        BigDecimal remaining = risk.subtract(recovered);

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "No outstanding amount remains");
        }

        if (amount.compareTo(remaining) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount exceeds the remaining amount at risk: "
                            + remaining);
        }

        RecoveryCollection collection = new RecoveryCollection();
        collection.setRecoveryCase(recoveryCase);
        collection.setAmount(amount);
        collection.setReferenceCode(reference);
        collection.setNote(note);

        RecoveryCollection saved = collectionRepository.save(collection);

        BigDecimal newRecovered = recovered.add(amount);
        BigDecimal newRemaining = risk.subtract(newRecovered);

        recoveryCase.setRecoveredAmount(newRecovered);

        if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.RECOVERED);
        } else {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.IN_PROGRESS);
        }

        caseRepository.save(recoveryCase);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "Collection recorded successfully");
        result.put("collectionId", saved.getId());
        result.put("caseId", caseId);
        result.put("amount", amount);
        result.put("referenceCode", reference);
        result.put("recoveredAmount", newRecovered);
        result.put("remainingAmount", newRemaining);
        result.put("caseStatus", recoveryCase.getStatus().name());

        return result;
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CollectionResponse> getAllCollections() {
        return collectionRepository.findAll().stream()
                .filter(c -> access.canAccess(c.getRecoveryCase()))
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/case/{caseId}")
    @Transactional(readOnly = true)
    public List<CollectionResponse> getCollectionsByCase(
            @PathVariable Long caseId) {

        access.requireCase(caseId);

        return collectionRepository.findByRecoveryCaseId(caseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private CollectionResponse toResponse(
            RecoveryCollection collection) {

        CollectionResponse response = new CollectionResponse();
        response.id = collection.getId();
        response.caseId = collection.getRecoveryCase().getId();
        response.amount = collection.getAmount();
        response.referenceCode = collection.getReferenceCode();
        response.confirmedAt = collection.getConfirmedAt();
        response.note = collection.getNote();

        return response;
    }

    public static class CollectionResponse {
        public Long id;
        public Long caseId;
        public BigDecimal amount;
        public String referenceCode;
        public LocalDateTime confirmedAt;
        public String note;
    }
}
