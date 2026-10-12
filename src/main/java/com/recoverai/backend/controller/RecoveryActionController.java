
package com.recoverai.backend.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.recoverai.backend.entity.RecoveryAction;
import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.repository.RecoveryActionRepository;
import com.recoverai.backend.repository.RecoveryCaseRepository;
import com.recoverai.backend.repository.RecoveryCollectionRepository;
import com.recoverai.backend.security.RecoveryCaseAccess;

@RestController
@RequestMapping("/api/recovery-actions")
public class RecoveryActionController {

    private static final int MAX_RECOVERY_ATTEMPTS = 3;

    private final RecoveryActionRepository recoveryActionRepository;
    private final RecoveryCaseRepository recoveryCaseRepository;
    private final RecoveryCollectionRepository collectionRepository;
    private final RecoveryCaseAccess access;

    public RecoveryActionController(
            RecoveryActionRepository recoveryActionRepository,
            RecoveryCaseRepository recoveryCaseRepository,
            RecoveryCollectionRepository collectionRepository,
            RecoveryCaseAccess access) {
        this.recoveryActionRepository = recoveryActionRepository;
        this.recoveryCaseRepository = recoveryCaseRepository;
        this.collectionRepository = collectionRepository;
        this.access = access;
    }

    @PostMapping("/for-case/{caseId}")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public RecoveryActionResponse createAction(
            @PathVariable Long caseId,
            @RequestParam RecoveryAction.ActionType actionType) {

        RecoveryCase recoveryCase = findCaseForUpdate(caseId);

        validateCaseAllowsActions(recoveryCase);

        if (actionType == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Action type is required");
        }

        if (actionType == RecoveryAction.ActionType.STOP
                || actionType == RecoveryAction.ActionType.ESCALATE_TO_HUMAN) {

            RecoveryAction action = new RecoveryAction();
            action.setRecoveryCase(recoveryCase);
            action.setActionType(actionType);
            action.setStatus(RecoveryAction.ActionStatus.PENDING);
            action.setAttemptNumber(recoveryCase.getRecoveryAttempts());
            action.setAmountRecovered(BigDecimal.ZERO);
            action.setResult("Action created and waiting for human approval");

            return toResponse(recoveryActionRepository.save(action));
        }

        if (recoveryCase.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Maximum of three recovery attempts reached. Human review is required.");
        }

        long activeActions = recoveryActionRepository
                .findByRecoveryCaseId(caseId)
                .stream()
                .filter(action ->
                        action.getStatus() == RecoveryAction.ActionStatus.PENDING
                        || action.getStatus() == RecoveryAction.ActionStatus.APPROVED
                        || action.getStatus() == RecoveryAction.ActionStatus.EXECUTED)
                .count();

        if (activeActions > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case already has an active recovery action");
        }

        RecoveryAction action = new RecoveryAction();
        action.setRecoveryCase(recoveryCase);
        action.setActionType(actionType);
        action.setStatus(RecoveryAction.ActionStatus.PENDING);
        action.setAttemptNumber(recoveryCase.getRecoveryAttempts() + 1);
        action.setAmountRecovered(BigDecimal.ZERO);
        action.setResult("Action created and waiting for approval");

        return toResponse(recoveryActionRepository.save(action));
    }

    @GetMapping
    public List<RecoveryActionResponse> getAllActions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page index cannot be negative");
        Pageable pageable = PageRequest.of(page, Math.max(1, Math.min(size, 100)), Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<RecoveryAction> visible = access.isAdmin()
                ? recoveryActionRepository.findAll(pageable)
                : recoveryActionRepository.findAllByRecoveryCase_Owner_Id(access.currentUserId(), pageable);
        return visible.map(this::toResponse).getContent();
    }

    @GetMapping("/{id}")
    public RecoveryActionResponse getAction(@PathVariable Long id) {
        return toResponse(findAction(id));
    }

    @GetMapping("/for-case/{caseId}")
    public List<RecoveryActionResponse> getActionsForCase(
            @PathVariable Long caseId) {

        access.requireCase(caseId);

        return recoveryActionRepository.findByRecoveryCaseId(caseId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PutMapping("/{id}/approve")
    @Transactional
    public RecoveryActionResponse approveAction(@PathVariable Long id) {
        RecoveryAction action = findAction(id);

        if (action.getStatus() != RecoveryAction.ActionStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only PENDING actions can be approved");
        }

        RecoveryCase recoveryCase = findCaseForUpdate(
                action.getRecoveryCase().getId());

        validateCaseAllowsActions(recoveryCase);

        if (isRecoveryAttempt(action.getActionType())
                && recoveryCase.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Maximum recovery attempts reached");
        }

        action.setStatus(RecoveryAction.ActionStatus.APPROVED);
        action.setResult("Action approved and ready for execution");

        return toResponse(recoveryActionRepository.save(action));
    }

    @PutMapping("/{id}/execute")
    @Transactional
    public RecoveryActionResponse executeAction(@PathVariable Long id) {
        RecoveryAction action = findAction(id);

        if (action.getStatus() != RecoveryAction.ActionStatus.APPROVED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only APPROVED actions can be executed");
        }

        RecoveryCase recoveryCase = findCaseForUpdate(
                action.getRecoveryCase().getId());

        validateCaseAllowsActions(recoveryCase);

        if (isRecoveryAttempt(action.getActionType())) {
            if (recoveryCase.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Maximum of three recovery attempts reached");
            }

            recoveryCase.setRecoveryAttempts(
                    recoveryCase.getRecoveryAttempts() + 1);

            action.setAttemptNumber(recoveryCase.getRecoveryAttempts());
            recoveryCaseRepository.save(recoveryCase);
        }

        if (action.getActionType() == RecoveryAction.ActionType.STOP) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.STOPPED);
            recoveryCaseRepository.save(recoveryCase);

            action.setStatus(RecoveryAction.ActionStatus.EXECUTED);
            action.setExecutedAt(LocalDateTime.now());
            action.setResult("Recovery stopped. Human review is required.");
        } else if (action.getActionType()
                == RecoveryAction.ActionType.ESCALATE_TO_HUMAN) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.ESCALATED);
            recoveryCaseRepository.save(recoveryCase);

            action.setStatus(RecoveryAction.ActionStatus.EXECUTED);
            action.setExecutedAt(LocalDateTime.now());
            action.setResult("Case escalated for human review.");
        } else {
            action.setStatus(RecoveryAction.ActionStatus.EXECUTED);
            action.setExecutedAt(LocalDateTime.now());
            action.setResult(
                    "Action executed. Verify the result and record confirmed collections separately.");
        }

        return toResponse(recoveryActionRepository.save(action));
    }

    @PutMapping("/{id}/success")
    @Transactional
    public RecoveryActionResponse markSuccess(@PathVariable Long id) {
        RecoveryAction action = findAction(id);

        if (action.getStatus() != RecoveryAction.ActionStatus.EXECUTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only EXECUTED actions can be marked SUCCESS");
        }

        RecoveryCase recoveryCase = findCaseForUpdate(
                action.getRecoveryCase().getId());

        if (action.getActionType() == RecoveryAction.ActionType.STOP
                || action.getActionType()
                        == RecoveryAction.ActionType.ESCALATE_TO_HUMAN) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "STOP and ESCALATE actions cannot be marked as ordinary recovery success");
        }

        BigDecimal confirmedAmount =
                collectionRepository.getConfirmedAmountByCaseId(
                        recoveryCase.getId());

        if (confirmedAmount == null) {
            confirmedAmount = BigDecimal.ZERO;
        }

        BigDecimal risk = recoveryCase.getRevenueAtRisk();

        if (risk == null || risk.compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Invalid amount at risk for this case");
        }

        recoveryCase.setRecoveredAmount(confirmedAmount);

        if (confirmedAmount.compareTo(risk) >= 0) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.RECOVERED);
        } else if (confirmedAmount.compareTo(BigDecimal.ZERO) > 0) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.IN_PROGRESS);
        } else if (recoveryCase.getStatus() != RecoveryCase.CaseStatus.STOPPED
                && recoveryCase.getStatus() != RecoveryCase.CaseStatus.ESCALATED
                && recoveryCase.getStatus() != RecoveryCase.CaseStatus.EXHAUSTED) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.OPEN);
        }

        recoveryCaseRepository.save(recoveryCase);

        action.setStatus(RecoveryAction.ActionStatus.SUCCESS);
        action.setAmountRecovered(BigDecimal.ZERO);
        action.setResult(
                "Action completed. Money recovered is counted only from confirmed collection records.");

        return toResponse(recoveryActionRepository.save(action));
    }

    @PutMapping("/{id}/failed")
    @Transactional
    public RecoveryActionResponse markFailed(
            @PathVariable Long id,
            @RequestParam(defaultValue = "Action failed") String reason) {

        RecoveryAction action = findAction(id);

        if (action.getStatus() != RecoveryAction.ActionStatus.EXECUTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only EXECUTED actions can be marked FAILED");
        }

        if (reason == null || reason.isBlank()) {
            reason = "Action failed";
        }

        action.setStatus(RecoveryAction.ActionStatus.FAILED);
        action.setResult(reason.trim());

        RecoveryCase recoveryCase = findCaseForUpdate(
                action.getRecoveryCase().getId());

        if (isRecoveryAttempt(action.getActionType())
                && recoveryCase.getRecoveryAttempts() >= MAX_RECOVERY_ATTEMPTS
                && recoveryCase.getStatus() != RecoveryCase.CaseStatus.RECOVERED) {
            recoveryCase.setStatus(RecoveryCase.CaseStatus.EXHAUSTED);
            recoveryCaseRepository.save(recoveryCase);
        }

        return toResponse(recoveryActionRepository.save(action));
    }

    private RecoveryCase findCase(Long caseId) {
        return access.requireCase(caseId);
    }

    private RecoveryCase findCaseForUpdate(Long caseId) {
        RecoveryCase c = recoveryCaseRepository.findByIdForUpdate(caseId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recovery case not found: " + caseId));
        access.requireAccess(c);
        return c;
    }

    private RecoveryAction findAction(Long id) {
        RecoveryAction action = recoveryActionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Recovery action not found: " + id));
        access.requireAccess(action.getRecoveryCase());
        return action;
    }

    private void validateCaseAllowsActions(RecoveryCase recoveryCase) {
        RecoveryCase.CaseStatus status = recoveryCase.getStatus();

        if (status == RecoveryCase.CaseStatus.RECOVERED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case is already recovered");
        }

        if (status == RecoveryCase.CaseStatus.STOPPED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case is stopped and requires human review");
        }

        if (status == RecoveryCase.CaseStatus.ESCALATED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case is escalated and requires human review");
        }

        if (status == RecoveryCase.CaseStatus.EXHAUSTED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "This case has exhausted its recovery attempts and requires human review");
        }
    }

    private boolean isRecoveryAttempt(
            RecoveryAction.ActionType actionType) {
        return actionType == RecoveryAction.ActionType.RETRY_PAYMENT
                || actionType == RecoveryAction.ActionType.SEND_REMINDER
                || actionType
                        == RecoveryAction.ActionType.GENERATE_PAYMENT_LINK;
    }

    private RecoveryActionResponse toResponse(RecoveryAction action) {
        RecoveryActionResponse response = new RecoveryActionResponse();

        response.id = action.getId();
        response.actionType = action.getActionType() != null
                ? action.getActionType().name() : null;
        response.status = action.getStatus() != null
                ? action.getStatus().name() : null;
        response.attemptNumber = action.getAttemptNumber();
        response.amountRecovered = action.getAmountRecovered();
        response.result = action.getResult();
        response.executedAt = action.getExecutedAt();
        response.createdAt = action.getCreatedAt();

        if (action.getRecoveryCase() != null) {
            response.recoveryCaseId = action.getRecoveryCase().getId();
        }

        return response;
    }

    public static class RecoveryActionResponse {
        public Long id;
        public String actionType;
        public String status;
        public int attemptNumber;
        public BigDecimal amountRecovered;
        public String result;
        public LocalDateTime executedAt;
        public LocalDateTime createdAt;
        public Long recoveryCaseId;
    }
}
