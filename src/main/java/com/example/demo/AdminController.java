package com.example.demo;

import com.example.demo.domain.AssistantRequest;
import com.example.demo.domain.IpRegistrationLimit;
import com.example.demo.repository.AssistantRequestRepository;
import com.example.demo.repository.DoctorRepository;
import com.example.demo.repository.IpRegistrationLimitRepository;
import com.example.demo.DTO.DoctorAdminDTO;
import com.example.demo.DTO.AssistantResponseDTO;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.domain.Review;
import org.springframework.data.domain.Sort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @Autowired
    private AssistantRequestRepository assistantRequestRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private IpRegistrationLimitRepository ipRegistrationLimitRepository;

    // ═══════════════════════════════════════════════
    // 🔔 NOTIFICATION BADGE — عدد الطلبات المعلقة
    // ═══════════════════════════════════════════════

    /**
     * ✅ يرجع عدد طلبات المساعدين + طلبات تسجيل الدكاترة المعلقة
     * (بيستخدمها الأدمن للـ notification badge)
     */
    @GetMapping("/pending-counts")
    public ResponseEntity<Map<String, Long>> getPendingCounts() {
        long pendingAssistants = assistantRequestRepository.countByStatus("PENDING");
        long pendingDoctors = doctorRepository.countByApprovedFalse();
        Map<String, Long> counts = new HashMap<>();
        counts.put("pendingAssistants", pendingAssistants);
        counts.put("pendingDoctors", pendingDoctors);
        counts.put("total", pendingAssistants + pendingDoctors);
        return ResponseEntity.ok(counts);
    }

    // ═══════════════════════════════════════════════
    // 💰 FINANCIAL STATS — إحصائيات مالية للأدمن
    // ═══════════════════════════════════════════════

    /**
     * ✅ عرض جميع الاشتراكات والحسابات (السارية والمنتهية)
     */
    @GetMapping("/financial-stats")
    public ResponseEntity<Map<String, Object>> getFinancialStats() {
        LocalDateTime now = LocalDateTime.now();

        List<Doctor> allDoctors = doctorRepository.findAll();
        
        long activeCount = allDoctors.stream()
                .filter(d -> d.getSubscriptionEndDate() != null 
                          && d.getSubscriptionEndDate().isAfter(now))
                .count();

        // Doctors who are already expired
        long alreadyExpiredCount = allDoctors.stream()
                .filter(d -> d.getSubscriptionEndDate() != null 
                          && d.getSubscriptionEndDate().isBefore(now))
                .count();

        Map<String, Object> stats = new HashMap<>();
        stats.put("activeCount", activeCount);
        stats.put("alreadyExpiredCount", alreadyExpiredCount);
        stats.put("totalExpectedRenewals", activeCount + alreadyExpiredCount);
        
        return ResponseEntity.ok(stats);
    }

    // ═══════════════════════════════════════════════
    // 🩺 DOCTOR REGISTRATION REQUESTS
    // ═══════════════════════════════════════════════

    /**
     * ✅ جلب كل الدكاترة اللي لسه متقبلوش
     * 🔒 يرجع DoctorAdminDTO (بالـ nationalId للإدارة) بدون activeToken أو password
     */
    @GetMapping("/doctor-requests")
    public ResponseEntity<List<DoctorAdminDTO>> getPendingDoctors() {
        List<DoctorAdminDTO> result = doctorRepository.findByApprovedFalse()
                .stream()
                .map(DoctorAdminDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * ✅ قبول تسجيل دكتور — بيولد specialAccessCode ويفعّل الحساب
     */
    @PutMapping("/doctor-requests/{nationalId}/approve")
    public ResponseEntity<?> approveDoctorRegistration(
            @PathVariable String nationalId,
            @RequestParam(defaultValue = "1") int months) {
        try {
            String code = doctorService.approveDoctorRegistration(nationalId, months);
            Map<String, String> response = new HashMap<>();
            response.put("message", "تم قبول الدكتور بنجاح");
            response.put("specialAccessCode", code);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * ✅ رفض تسجيل دكتور — يحذفه من الداتابيز
     */
    @DeleteMapping("/doctor-requests/{nationalId}/reject")
    public ResponseEntity<?> rejectDoctorRegistration(@PathVariable String nationalId) {
        Optional<Doctor> doctorOpt = doctorRepository.findByNationalId(nationalId);
        if (doctorOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            doctorService.deleteDoctorCompletely(nationalId);
            return ResponseEntity.ok("تم رفض وحذف طلب تسجيل الدكتور");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("تعذر الحذف: " + e.getMessage());
        }
    }

    /**
     * ✅ جلب كل الدكاترة المسجلين في النظام (للاشتراكات والإدارة)
     * 🔒 يرجع DoctorAdminDTO بدون activeToken أو password
     */
    @GetMapping("/doctors")
    public ResponseEntity<List<DoctorAdminDTO>> getAllDoctors() {
        List<DoctorAdminDTO> result = doctorRepository.findAll()
                .stream()
                .map(DoctorAdminDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * ✅ تفعيل أو تجديد اشتراك دكتور
     */
    @PutMapping("/doctors/{nationalId}/renew")
    public ResponseEntity<?> renewDoctorSubscription(
            @PathVariable String nationalId,
            @RequestParam(defaultValue = "1") int months) {
        Optional<Doctor> doctorOpt = doctorRepository.findByNationalId(nationalId);
        if (doctorOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Doctor doctor = doctorOpt.get();
        doctor.setApproved(true);
        doctor.setEnabled(true);
        
        // Calculate new end date from today, or extend from existing if still valid
        LocalDateTime now = LocalDateTime.now();
        if (doctor.getSubscriptionEndDate() != null && doctor.getSubscriptionEndDate().isAfter(now)) {
            doctor.setSubscriptionEndDate(doctor.getSubscriptionEndDate().plusMonths(months));
        } else {
            doctor.setSubscriptionEndDate(now.plusMonths(months));
        }
        
        doctorRepository.save(doctor);
        return ResponseEntity.ok("تم تجديد اشتراك الدكتور بنجاح لمدة " + months + " شهور");
    }

    /**
     * ✅ حذف دكتور من النظام وجميع بياناته المرتبطة بالكامل
     */
    @DeleteMapping("/doctors/{nationalId}")
    public ResponseEntity<?> deleteRegisteredDoctor(@PathVariable String nationalId) {
        try {
            doctorService.deleteDoctorCompletely(nationalId);
            return ResponseEntity.ok("تم مسح الطبيب وجميع البيانات، الحجوزات، الروشتات، والمساعدين المرتبطين به بنجاح.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("تعذر العمل: " + e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════
    // 👥 ASSISTANT REQUESTS
    // ═══════════════════════════════════════════════

    /**
     * ✅ عرض كل طلبات إضافة المساعدين (للمدير)
     * 🔒 يرجع AssistantResponseDTO بدون activeToken
     */
    @GetMapping("/assistant-requests")
    public ResponseEntity<List<AssistantResponseDTO>> getAllRequests() {
        List<AssistantResponseDTO> result = assistantRequestRepository.findAll()
                .stream()
                .map(AssistantResponseDTO::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * ✅ الموافقة أو الرفض على مساعد
     */
    @PutMapping("/assistant-requests/{id}/{status}")
    public ResponseEntity<?> updateAssistantRequestStatus(@PathVariable Long id, @PathVariable String status) {
        String newStatus = status.toUpperCase();
        if (!newStatus.equals("APPROVED") && !newStatus.equals("REJECTED")) {
            return ResponseEntity.badRequest().body("حالة غير صحيحة");
        }

        Optional<AssistantRequest> requestOpt = assistantRequestRepository.findById(id);
        if (requestOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        AssistantRequest request = requestOpt.get();

        if (newStatus.equals("APPROVED")) {
            long currentApprovedCount = assistantRequestRepository.countByDoctorNationalIdAndStatus(request.getDoctorNationalId(), "APPROVED");
            if (!request.getStatus().equals("APPROVED") && currentApprovedCount >= 3) {
                return ResponseEntity.badRequest().body("الدكتور لديه 3 مساعدين معتمدين بالفعل. لا يمكنك الموافقة على المزيد.");
            }
        }

        request.setStatus(newStatus);
        assistantRequestRepository.save(request);
        return ResponseEntity.ok("تم " + (newStatus.equals("APPROVED") ? "الموافقة على" : "رفض") + " الطلب بنجاح");
    }

    /**
     * ✅ حذف مساعد
     */
    @DeleteMapping("/assistant-requests/{id}")
    public ResponseEntity<?> deleteAssistantRequest(@PathVariable Long id) {
        if (!assistantRequestRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        assistantRequestRepository.deleteById(id);
        return ResponseEntity.ok("تم حذف الطلب بنجاح");
    }

    // ═══════════════════════════════════════════════
    // 💬 REVIEWS / COMMENTS
    // ═══════════════════════════════════════════════

    /**
     * ✅ جلب كل التعليقات (للمدير)
     */
    @GetMapping("/reviews")
    public ResponseEntity<List<Review>> getAllReviews() {
        List<Review> reviews = reviewRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(reviews);
    }

    /**
     * ✅ حذف تعليق معين أو مسيء
     */
    @DeleteMapping("/reviews/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id) {
        if (!reviewRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        reviewRepository.deleteById(id);
        return ResponseEntity.ok("تم حذف التعليق بنجاح");
    }

    // ═══════════════════════════════════════════════
    // 🔐 IP REGISTRATION LIMITS — إدارة حدود التسجيل
    // ═══════════════════════════════════════════════

    /**
     * ✅ قائمة كل الـ IPs اللي تجاوزت 0 تسجيل، مرتبة تنازليًا بالعدد
     */
    @GetMapping("/ip-limits")
    public ResponseEntity<List<Map<String, Object>>> getAllIpLimits() {
        List<Map<String, Object>> result = ipRegistrationLimitRepository.findAll()
                .stream()
                .sorted(Comparator.comparingInt(IpRegistrationLimit::getRegistrationCount).reversed())
                .map(rec -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("ip",               rec.getIpAddress());
                    m.put("count",            rec.getRegistrationCount());
                    m.put("firstRegistered",  rec.getFirstRegisteredAt());
                    m.put("lastRegistered",   rec.getLastRegisteredAt());
                    m.put("limitReached",     rec.getRegistrationCount() >= 5);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    /**
     * ✅ مسح سجل IP — بيسمح للمستخدم يسجّل من جديد (reset)
     * يفيد مثلاً لو حد غلطًا أو في IP شبكة مشتركة (مستشفى، مدرسة)
     */
    @DeleteMapping("/ip-limits/{ip}")
    public ResponseEntity<?> resetIpLimit(@PathVariable String ip) {
        if (!ipRegistrationLimitRepository.existsById(ip)) {
            return ResponseEntity.notFound().build();
        }
        ipRegistrationLimitRepository.deleteById(ip);
        System.out.println("✅ Admin reset IP limit for: " + ip);
        return ResponseEntity.ok("تم مسح سجل التسجيل لـ IP: " + ip + ". يمكنه الآن تسجيل 5 حسابات جديدة.");
    }

    /**
     * ✅ تعديل العداد يدويًا — مفيد لو شبكة مشتركة (IP مستشفى) محتاجة حد أعلى
     * مثلاً: PUT /api/admin/ip-limits/1.2.3.4/set-count?count=0
     */
    @PutMapping("/ip-limits/{ip}/set-count")
    public ResponseEntity<?> setIpCount(
            @PathVariable String ip,
            @RequestParam int count) {
        if (count < 0) return ResponseEntity.badRequest().body("العدد لا يمكن أن يكون سالبًا.");
        IpRegistrationLimit record = ipRegistrationLimitRepository
                .findById(ip)
                .orElse(new IpRegistrationLimit(ip));
        record.setRegistrationCount(count);
        record.setLastRegisteredAt(LocalDateTime.now());
        ipRegistrationLimitRepository.save(record);
        System.out.println("✅ Admin set IP count for " + ip + " to " + count);
        return ResponseEntity.ok("تم تعديل عداد التسجيل لـ IP " + ip + " إلى " + count + "/5.");
    }
}
