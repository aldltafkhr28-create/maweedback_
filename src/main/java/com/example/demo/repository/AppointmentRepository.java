package com.example.demo.repository;

import com.example.demo.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {
    
    // جلب حجوزات الطبيب بناءً على الرقم القومي الخاص به
    List<Appointment> findByDoctorNationalId(String doctorNationalId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByDoctorNationalId(String doctorNationalId);


    // جلب حجوزات المريض بناءً على الرقم القومي الخاص به
    List<Appointment> findByPatientNationalId(String patientNationalId);

    // جلب كل مواعيد الطبيب في يوم معين (لفحص التعارض)
    @Query("SELECT a FROM Appointment a WHERE a.doctorNationalId = :doctorNationalId AND a.appointmentDate = :date")
    List<Appointment> findByDoctorNationalIdAndAppointmentDate(
            @Param("doctorNationalId") String doctorNationalId,
            @Param("date") LocalDate date
    );

    // جلب كل مواعيد المريض مع طبيب محدد في فترة الثلاثة أيام المتاحة للحجز 
    @Query("SELECT a FROM Appointment a WHERE a.patientNationalId = :patientNationalId AND a.doctorNationalId = :doctorNationalId AND a.appointmentDate >= :startDate AND a.appointmentDate <= :endDate")
    List<Appointment> findByPatientAndDoctorInDateRange(
            @Param("patientNationalId") String patientNationalId,
            @Param("doctorNationalId") String doctorNationalId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    // 🔒 فحص الحجز في آخر 48 ساعة بناءً على وقت الحجز (createdAt)
    @Query("SELECT a FROM Appointment a WHERE a.patientNationalId = :patientNationalId AND a.doctorNationalId = :doctorNationalId AND a.createdAt >= :since AND a.status NOT IN ('REJECTED', 'CANCELLED')")
    List<Appointment> findRecentActiveBookings(
            @Param("patientNationalId") String patientNationalId,
            @Param("doctorNationalId") String doctorNationalId,
            @Param("since") LocalDateTime since
    );

    // ✅ جلب المواعيد اللي محتاجة تذكير (اللي ميعادها في نافذة الـ 20 دقيقة الجاية)
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate = :date AND a.appointmentTime >= :fromTime AND a.appointmentTime <= :toTime AND a.status = 'PENDING' AND a.reminderSent = false")
    List<Appointment> findRemindersToSend(
            @Param("date") LocalDate date,
            @Param("fromTime") LocalTime fromTime,
            @Param("toTime") LocalTime toTime
    );

    // ✅ جلب الحجوزات المستقبلية المراد ترحيلها
    @Query("SELECT a FROM Appointment a WHERE a.doctorNationalId = :doctorNationalId AND a.appointmentDate >= :startDate AND a.status NOT IN ('DONE', 'REJECTED', 'CANCELLED', 'NO_SHOW') ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<Appointment> findPendingAppointmentsFromDate(
            @Param("doctorNationalId") String doctorNationalId,
            @Param("startDate") LocalDate startDate
    );

    // ✅ جلب المواعيد المنتهية والتي لم يحضر أصحابها (لعمليات تسجيل الغياب التلقائي)
    @Query("SELECT a FROM Appointment a WHERE a.appointmentDate < :today AND a.status IN ('PENDING', 'APPROVED', 'ARRIVED')")
    List<Appointment> findPastUncompletedAppointments(@Param("today") LocalDate today);
}
