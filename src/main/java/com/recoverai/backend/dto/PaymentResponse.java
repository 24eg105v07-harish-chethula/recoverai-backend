package com.recoverai.backend.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.recoverai.backend.entity.Payment;

public class PaymentResponse {

    private Long id;
    private BigDecimal amount;
    private String razorpayOrderId;
    private String razorpayPaymentId;
    private Payment.PaymentStatus status;
    private String failureReason;
    private LocalDateTime createdAt;

    private Long customerId;
    private String customerName;
    private String customerEmail;
    private String customerPhone;

    public PaymentResponse() {
    }

    public PaymentResponse(
            Long id,
            BigDecimal amount,
            String razorpayOrderId,
            String razorpayPaymentId,
            Payment.PaymentStatus status,
            String failureReason,
            LocalDateTime createdAt,
            Long customerId,
            String customerName,
            String customerEmail,
            String customerPhone) {

        this.id = id;
        this.amount = amount;
        this.razorpayOrderId = razorpayOrderId;
        this.razorpayPaymentId = razorpayPaymentId;
        this.status = status;
        this.failureReason = failureReason;
        this.createdAt = createdAt;
        this.customerId = customerId;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.customerPhone = customerPhone;
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getRazorpayOrderId() {
        return razorpayOrderId;
    }

    public String getRazorpayPaymentId() {
        return razorpayPaymentId;
    }

    public Payment.PaymentStatus getStatus() {
        return status;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }
}