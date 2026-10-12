package com.recoverai.backend.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.recoverai.backend.entity.AIAnalysis;

public interface AIAnalysisRepository extends JpaRepository<AIAnalysis, Long> {

    Optional<AIAnalysis> findByRecoveryCaseId(Long recoveryCaseId);
}