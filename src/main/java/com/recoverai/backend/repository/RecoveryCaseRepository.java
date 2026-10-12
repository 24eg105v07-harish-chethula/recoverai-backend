package com.recoverai.backend.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.recoverai.backend.entity.RecoveryCase;
import com.recoverai.backend.entity.RecoveryCase.CaseStatus;

public interface RecoveryCaseRepository extends JpaRepository<RecoveryCase, Long> {

    @Query("""
        select count(c),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.OPEN then 1 else 0 end),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.IN_PROGRESS then 1 else 0 end),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.RECOVERED then 1 else 0 end),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.ESCALATED then 1 else 0 end),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.STOPPED then 1 else 0 end),
            sum(case when c.status = com.recoverai.backend.entity.RecoveryCase.CaseStatus.EXHAUSTED then 1 else 0 end),
            coalesce(sum(c.revenueAtRisk), 0)
        from RecoveryCase c
        where (:ownerId is null or c.owner.id = :ownerId)
        """)
    List<Object[]> getDashboardStats(@Param("ownerId") Long ownerId);

    List<RecoveryCase> findByStatus(CaseStatus status);

    Optional<RecoveryCase> findByPaymentId(Long paymentId);
    List<RecoveryCase> findAllByOwnerId(Long ownerId);
    Page<RecoveryCase> findAllByOwnerId(Long ownerId, Pageable pageable);
    Optional<RecoveryCase> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);

    // Serialize state-changing operations on a case so parallel requests
    // cannot both pass the recovery-attempt limit.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from RecoveryCase c where c.id = :id")
    Optional<RecoveryCase> findByIdForUpdate(@Param("id") Long id);
}
