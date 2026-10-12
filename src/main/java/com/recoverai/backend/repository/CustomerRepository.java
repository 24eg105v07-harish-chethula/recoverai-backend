package com.recoverai.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.recoverai.backend.entity.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {
}