package com.example.demo.repository;

import com.example.demo.MedicalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalRequestRepository extends JpaRepository<MedicalRequest, Long> {

    // جلب كل الطلبات الخاصة بمريض معين
    List<MedicalRequest> findByPatientNationalIdOrderByCreatedAtDesc(String patientNationalId);

    // جلب كل الطلبات التي أنشأها طبيب معين
    List<MedicalRequest> findByDoctorNationalIdOrderByCreatedAtDesc(String doctorNationalId);

    // جلب الطلبات المعلقة (غير المنجزة) لمريض معين
    List<MedicalRequest> findByPatientNationalIdAndIsFulfilledFalseOrderByCreatedAtDesc(String patientNationalId);

    // جلب طلبات طبيب معين لمريض بعينه
    List<MedicalRequest> findByDoctorNationalIdAndPatientNationalIdOrderByCreatedAtDesc(
            String doctorNationalId, String patientNationalId);
}
