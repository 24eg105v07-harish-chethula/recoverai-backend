package com.recoverai.backend.repository;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.recoverai.backend.entity.RecoveryCollection;

public interface RecoveryCollectionRepository
        extends JpaRepository<RecoveryCollection, Long> {

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM RecoveryCollection c")
    BigDecimal getTotalConfirmedAmount();

    @Query("SELECT COALESCE(SUM(c.amount), 0) FROM RecoveryCollection c WHERE c.recoveryCase.owner.id = :ownerId")
    BigDecimal getTotalConfirmedAmountByOwnerId(@Param("ownerId") Long ownerId);

    @Query("""
        SELECT COALESCE(SUM(c.amount), 0)
        FROM RecoveryCollection c
        WHERE c.recoveryCase.id = :caseId
        """)
    BigDecimal getConfirmedAmountByCaseId(
            @Param("caseId") Long caseId);

    boolean existsByReferenceCode(String referenceCode);

    List<RecoveryCollection> findByRecoveryCaseId(Long caseId);
}

