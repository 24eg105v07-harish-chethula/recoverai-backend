package com.recoverai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.recoverai.backend.entity.AuditEvent;

public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {

    List<AuditEvent> findByRecoveryCaseIdOrderByCreatedAtAsc(Long recoveryCaseId);
}