package com.example.demo;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * ✅ MedicalRequest — يمثل طلب أشعة أو تحليل يصدره الطبيب للمريض
 * الطبيب يملأ البيانات (اسم الأشعة، المكان المقترح، اللوكيشن)
 * المريض يرفع الصورة عند إتمامها، وتُحفظ مشفرة على السيرفر
 */
@Entity
@Table(name = "medical_requests")
@Data
@NoArgsConstructor
public class MedicalRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // — بيانات المريض والطبيب —
    @Column(nullable = false)
    private String patientNationalId;

    @Column(nullable = true)
    private String patientName;

    @Column(nullable = false)
    private String doctorNationalId;

    @Column(nullable = true)
    private String doctorName;

    // — تفاصيل الطلب —
    // نوع الطلب: "XRAY" أو "LAB" (أشعة أو تحليل)
    @Column(nullable = false)
    private String requestType;

    // اسم الأشعة أو التحليل (مثال: "أشعة مقطعية")
    @Column(nullable = false)
    private String investigationName;

    // المنطقة أو تفاصيل إضافية (مثال: "على الصدر والرئتين")
    @Column(nullable = true)
    private String targetArea;

    // — المكان المقترح من الطبيب —
    // اسم المكان (مثال: "مركز ألفا للأشعة")
    @Column(nullable = true)
    private String recommendedPlaceName;

    // رابط المكان على جوجل ماب لتسهيل الوصول على المريض
    @Column(nullable = true, length = 1000)
    private String recommendedPlaceLocationUrl;

    // — حالة الطلب —
    // هل قام المريض بإتمام الأشعة ورفع النتيجة؟
    @Column(columnDefinition = "boolean default false")
    private Boolean isFulfilled = false;

    // مسار الملف المشفر على السيرفر (يُعبأ بعد رفع المريض للصورة)
    @Column(nullable = true, length = 1000)
    private String encryptedFilePath;

    // نوع الملف المرفوع (مثال: "image/jpeg", "application/pdf")
    @Column(nullable = true)
    private String fileContentType;

    // ملاحظة المريض عند رفع الصورة
    @Column(nullable = true, columnDefinition = "TEXT")
    private String patientNote;

    // تاريخ إنشاء الطلب من الطبيب
    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    // تاريخ رفع المريض للصورة
    @Column(name = "fulfilled_at")
    private LocalDateTime fulfilledAt;
}
