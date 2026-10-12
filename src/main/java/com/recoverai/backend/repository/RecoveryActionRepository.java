package com.recoverai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.recoverai.backend.entity.RecoveryAction;
import com.recoverai.backend.entity.RecoveryAction.ActionStatus;

public interface RecoveryActionRepository extends JpaRepository<RecoveryAction, Long> {

    @Query("""
        select count(a),
            sum(case when a.status = com.recoverai.backend.entity.RecoveryAction.ActionStatus.PENDING then 1 else 0 end),
            sum(case when a.status = com.recoverai.backend.entity.RecoveryAction.ActionStatus.APPROVED then 1 else 0 end),
            sum(case when a.status = com.recoverai.backend.entity.RecoveryAction.ActionStatus.EXECUTED then 1 else 0 end),
            sum(case when a.status = com.recoverai.backend.entity.RecoveryAction.ActionStatus.SUCCESS then 1 else 0 end),
            sum(case when a.status = com.recoverai.backend.entity.RecoveryAction.ActionStatus.FAILED then 1 else 0 end)
        from RecoveryAction a
        where (:ownerId is null or a.recoveryCase.owner.id = :ownerId)
        """)
    List<Object[]> getDashboardStats(@Param("ownerId") Long ownerId);

    List<RecoveryAction> findByRecoveryCaseId(Long recoveryCaseId);
    Page<RecoveryAction> findAllByRecoveryCase_Owner_Id(Long ownerId, Pageable pageable);

    List<RecoveryAction> findByStatus(ActionStatus status);
}
