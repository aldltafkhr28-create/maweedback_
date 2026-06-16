package com.example.demo;

import com.example.demo.Appointment;
import com.example.demo.domain.Review;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.DoctorRepository;
import com.example.demo.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    /**
     * جلب أحدث التقييمات العالية (4 نجوم أو أكثر) للواجهة الرئيسية (بدون مصادقة)
     */
    @GetMapping("/recent-high")
    public ResponseEntity<List<Review>> getRecentHighReviews() {
        List<Review> reviews = reviewRepository.findTop10ByRatingGreaterThanEqualOrderByCreatedAtDesc(4);
        return ResponseEntity.ok(reviews);
    }

    /**
     * جلب أحدث 10 تعليقات بدون فلتر للعرض في داشبورد اليوزر
     */
    @GetMapping("/recent")
    public ResponseEntity<List<Review>> getRecentReviews() {
        List<Review> reviews = reviewRepository.findTop10ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(reviews);
    }

    /**
     * إضافة تقييم جديد من المريض بعد إتمام الكشف
     */
    @PostMapping
    public ResponseEntity<?> addReview(@RequestBody Map<String, Object> payload) {
        try {
            Long appointmentId = Long.valueOf(payload.get("appointmentId").toString());
            Integer rating = Integer.valueOf(payload.get("rating").toString());
            String comment = payload.getOrDefault("comment", "").toString();

            if (rating < 1 || rating > 5) {
                return ResponseEntity.badRequest().body("التقييم يجب أن يكون بين 1 و 5 نجوم.");
            }

            // التحقق من أن المستخدم مسجل وتحديد هويته (مريض)
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equals("ROLE_PATIENT"))) {
                return ResponseEntity.status(403).body("صلاحية غير كافية لتقييم الطبيب.");
            }
            String curUserNatId = auth.getName();

            // فحص الحجز
            Optional<Appointment> optAppt = appointmentRepository.findById(appointmentId);
            if (optAppt.isEmpty()) {
                return ResponseEntity.badRequest().body("الحجز غير موجود.");
            }
            Appointment appointment = optAppt.get();

            // التأكد إن الحجز يخص المريض
            if (!appointment.getPatientNationalId().equals(curUserNatId)) {
                return ResponseEntity.status(403).body("لا يمكنك تقييم كشف لا يخصك.");
            }

            // التأكد من إتمام الكشف
            if (!"DONE".equals(appointment.getStatus())) {
                return ResponseEntity.badRequest().body("لا يمكنك تقييم الطبيب إلا بعد إتمام الكشف (DONE).");
            }

            // التأكد إن الكشف لم يتم تقييمه من قبل
            if (Boolean.TRUE.equals(appointment.getIsReviewed())) {
                return ResponseEntity.badRequest().body("لقد قمت بتقييم هذا الكشف مسبقاً.");
            }

            // حفظ التقييم في قاعدة البيانات
            Review review = new Review();
            review.setDoctorNationalId(appointment.getDoctorNationalId());
            review.setPatientNationalId(curUserNatId);
            review.setPatientName(appointment.getPatientName());
            review.setDoctorName(appointment.getDoctorName());
            review.setRating(rating);
            review.setComment(comment);
            reviewRepository.save(review);

            // تحديث حالة الحجز
            appointment.setIsReviewed(true);
            appointmentRepository.save(appointment);

            // تحديث متوسط التقييم للطبيب
            doctorRepository.findByNationalId(appointment.getDoctorNationalId()).ifPresent(doctor -> {
                int totalRs = doctor.getTotalReviews() == null ? 0 : doctor.getTotalReviews();
                double avgR = doctor.getAverageRating() == null ? 0.0 : doctor.getAverageRating();
                
                double newAvg = ((avgR * totalRs) + rating) / (totalRs + 1);
                
                doctor.setTotalReviews(totalRs + 1);
                // تقريب لخانة عشرية واحدة
                doctor.setAverageRating(Math.round(newAvg * 10.0) / 10.0);
                
                doctorRepository.save(doctor);
            });

            return ResponseEntity.ok("تم حفظ التقييم بنجاح. شكراً لك!");

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("حدث خطأ أثناء حفظ التقييم: " + e.getMessage());
        }
    }
}
