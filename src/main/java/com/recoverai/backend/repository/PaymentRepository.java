package com.recoverai.backend.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.recoverai.backend.entity.Payment;
import com.recoverai.backend.entity.Payment.PaymentStatus;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByStatus(PaymentStatus status);
    List<Payment> findByOwnerIdAndStatus(Long ownerId, PaymentStatus status);
    Page<Payment> findByOwnerIdAndStatus(Long ownerId, PaymentStatus status, Pageable pageable);
    java.util.Optional<Payment> findByIdAndOwnerId(Long id, Long ownerId);
    long countByOwnerId(Long ownerId);
}
