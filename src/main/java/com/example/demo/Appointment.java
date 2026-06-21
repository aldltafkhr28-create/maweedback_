package com.example.demo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "appointments")
@Data
@NoArgsConstructor
public class Appointment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String patientNationalId;

    @Column(nullable = false)
    private String patientName;

    @Column(nullable = false)
    private String doctorNationalId;

    @Column(nullable = false)
    private String doctorName;

    @Column(nullable = false)
    private LocalDate appointmentDate;

    @Column(nullable = false)
    private LocalTime appointmentTime;

    // حالة الحجز: PENDING, APPROVED, REJECTED
    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = true)
    private String visitType; // كشف أو إعادة

    @Column(columnDefinition = "boolean default false")
    private Boolean isReviewed = false;

    // لتسجيل أي مبالغ تم دفعها خلال هذا الموعد (مثل أقساط العمليات)
    @Column(nullable = true)
    private Double paidAmount = 0.0;

    // ✅ علم التذكير — عشان السيرفر مابعتش الإشعار أكتر من مرة
    @Column(columnDefinition = "boolean default false")
    private Boolean reminderSent = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneId.of("Africa/Cairo"));
        }
    }
}
