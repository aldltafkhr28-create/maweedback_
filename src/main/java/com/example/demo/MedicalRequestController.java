package com.example.demo;

import com.example.demo.repository.MedicalRequestRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * ✅ MedicalRequestController — إدارة طلبات الأشعة والتحاليل بين الطبيب والمريض
 *
 * Workflow:
 * 1. الطبيب ينشئ طلب (POST /api/medical-requests) بعد الكشف
 * 2. المريض يشوف طلباته (GET /api/medical-requests/my)
 * 3. المريض يرفع النتيجة (POST /api/medical-requests/{id}/upload) - تُشفَّر وتُحفظ
 * 4. الطبيب يشوف النتيجة (GET /api/medical-requests/image/{id}) - تُفك شفرتها وتُعرض
 *
 * 🔒 IDOR Protection: كل endpoint يتحقق من هوية الـ JWT قبل السماح بالوصول
 */
@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/medical-requests")
public class MedicalRequestController {

    @Autowired
    private MedicalRequestRepository medicalRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    @Autowired
    private FileEncryptionUtil fileEncryptionUtil;

    // مجلد حفظ ملفات الأشعة المشفرة (منفصل عن باقي الصور لزيادة الأمان)
    private static final String MEDICAL_FILES_DIR = "uploads/medical-files";

    // ====================================================================
    // 🔐 Security Helpers — نفس نمط AppointmentController
    // ====================================================================

    private String getCurrentUser() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && !auth.getPrincipal().equals("anonymousUser")) ? auth.getName() : null;
    }

    private String getCurrentRole() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getAuthorities().isEmpty() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getAuthorities().iterator().next().getAuthority();
        }
        return null;
    }

    private boolean isDoctor()   { return "ROLE_DOCTOR".equals(getCurrentRole()); }
    private boolean isPatient()  { return "ROLE_PATIENT".equals(getCurrentRole()); }

    // ====================================================================
    // 📌 1. الطبيب: إنشاء طلب أشعة / تحليل جديد
    // ====================================================================

    /**
     * POST /api/medical-requests
     * يستخدمه الطبيب فقط لإنشاء طلب أشعة للمريض بعد انتهاء الكشف.
     *
     * 🔒 الحماية: يستخرج doctorNationalId من الـ JWT مباشرة — لا يثق بالـ Body
     *
     * Request Body (JSON):
     * {
     *   "patientNationalId": "12345678901234",
     *   "requestType": "XRAY",
     *   "investigationName": "أشعة مقطعية",
     *   "targetArea": "على الصدر والرئتين",
     *   "recommendedPlaceName": "مركز ألفا للأشعة",
     *   "recommendedPlaceLocationUrl": "https://maps.google.com/?q=..."
     * }
     */
    @PostMapping
    public ResponseEntity<?> createMedicalRequest(@RequestBody Map<String, String> body) {
        // 🔒 التحقق: الطبيب فقط يمكنه إنشاء طلب
        if (!isDoctor()) {
            return ResponseEntity.status(403).body(Map.of("error", "هذه العملية مخصصة للأطباء فقط."));
        }

        String doctorNationalId = getCurrentUser(); // 🔐 من الـ JWT — لا نثق بالـ Body
        String patientNationalId = body.get("patientNationalId");

        if (patientNationalId == null || patientNationalId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "الرقم القومي للمريض مطلوب."));
        }
        if (body.get("investigationName") == null || body.get("investigationName").isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "اسم الأشعة أو التحليل مطلوب."));
        }

        MedicalRequest request = new MedicalRequest();
        request.setDoctorNationalId(doctorNationalId);
        request.setPatientNationalId(patientNationalId.trim());
        request.setRequestType(body.getOrDefault("requestType", "XRAY").toUpperCase());
        request.setInvestigationName(body.get("investigationName").trim());
        request.setTargetArea(body.get("targetArea"));
        request.setRecommendedPlaceName(body.get("recommendedPlaceName"));
        request.setRecommendedPlaceLocationUrl(body.get("recommendedPlaceLocationUrl"));

        // جلب أسماء الطبيب والمريض من قاعدة البيانات
        userRepository.findByNationalId(patientNationalId.trim())
                .ifPresent(p -> request.setPatientName(p.getFullName()));
        
        // جلب اسم الطبيب من DoctorRepository
        doctorRepository.findByNationalId(doctorNationalId)
                .ifPresent(d -> request.setDoctorName(d.getNameDoctor()));

        MedicalRequest saved = medicalRequestRepository.save(request);

        // 🔔 إرسال Push Notification للمريض فوراً
        String requestTypeArabic = "XRAY".equals(request.getRequestType()) ? "أشعة" : "تحليل";
        String notificationTitle = "🧪 طلب " + requestTypeArabic + " جديد من طبيبك";
        String notificationBody = "طلب منك دكتور " + request.getDoctorName() +
                " إجراء: " + request.getInvestigationName() +
                (request.getTargetArea() != null ? " (" + request.getTargetArea() + ")" : "") +
                ". افتح التطبيق لعرض التفاصيل والمكان المقترح.";

        try {
            pushNotificationService.sendToUser(patientNationalId.trim(), notificationTitle, notificationBody);
        } catch (Exception e) {
            System.err.println("⚠️ فشل إرسال Push للمريض: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "message", "تم إنشاء الطلب بنجاح وإرسال إشعار للمريض.",
                "requestId", saved.getId()
        ));
    }

    // ====================================================================
    // 📌 2. المريض: جلب طلباته (المستخرج من الـ JWT تلقائياً)
    // ====================================================================

    /**
     * GET /api/medical-requests/my
     * 🔒 المريض يجلب طلباته فقط — الـ nationalId من الـ JWT وليس من الـ URL
     */
    @GetMapping("/my")
    public ResponseEntity<?> getMyRequests() {
        if (!isPatient()) {
            return ResponseEntity.status(403).body(Map.of("error", "هذه العملية مخصصة للمرضى فقط."));
        }
        String patientNationalId = getCurrentUser();
        List<MedicalRequest> requests = medicalRequestRepository
                .findByPatientNationalIdOrderByCreatedAtDesc(patientNationalId);
        return ResponseEntity.ok(requests);
    }

    /**
     * GET /api/medical-requests/my/pending
     * 🔒 المريض يجلب طلباته المعلقة فقط (للتنبيهات)
     */
    @GetMapping("/my/pending")
    public ResponseEntity<?> getMyPendingRequests() {
        if (!isPatient()) {
            return ResponseEntity.status(403).body(Map.of("error", "هذه العملية مخصصة للمرضى فقط."));
        }
        String patientNationalId = getCurrentUser();
        List<MedicalRequest> requests = medicalRequestRepository
                .findByPatientNationalIdAndIsFulfilledFalseOrderByCreatedAtDesc(patientNationalId);
        return ResponseEntity.ok(requests);
    }

    // ====================================================================
    // 📌 3. الطبيب: جلب طلبات مريض بعينه (IDOR Protected)
    // ====================================================================

    /**
     * GET /api/medical-requests/doctor/patient/{patientNationalId}
     * 🔒 الطبيب يجلب طلباته الخاصة لمريض معين فقط — يستخرج doctorId من الـ JWT
     */
    @GetMapping("/doctor/patient/{patientNationalId}")
    public ResponseEntity<?> getDoctorRequestsForPatient(@PathVariable String patientNationalId) {
        if (!isDoctor()) {
            return ResponseEntity.status(403).body(Map.of("error", "هذه العملية مخصصة للأطباء فقط."));
        }
        String doctorNationalId = getCurrentUser(); // 🔐 من الـ JWT فقط
        List<MedicalRequest> requests = medicalRequestRepository
                .findByDoctorNationalIdAndPatientNationalIdOrderByCreatedAtDesc(
                        doctorNationalId, patientNationalId.trim());
        return ResponseEntity.ok(requests);
    }

    /**
     * GET /api/medical-requests/doctor/all
     * 🔒 الطبيب يجلب كل طلباته الخاصة — يستخرج doctorId من الـ JWT
     */
    @GetMapping("/doctor/all")
    public ResponseEntity<?> getAllDoctorRequests() {
        if (!isDoctor()) {
            return ResponseEntity.status(403).body(Map.of("error", "هذه العملية مخصصة للأطباء فقط."));
        }
        String doctorNationalId = getCurrentUser(); // 🔐 من الـ JWT فقط
        List<MedicalRequest> requests = medicalRequestRepository
                .findByDoctorNationalIdOrderByCreatedAtDesc(doctorNationalId);
        return ResponseEntity.ok(requests);
    }

    // ====================================================================
    // 📌 4. المريض: رفع صورة الأشعة / التحليل (تُشفَّر تلقائياً)
    // ====================================================================

    /**
     * POST /api/medical-requests/{requestId}/upload
     * 🔒 الحماية: التحقق أن هذا الطلب يخص المريض المسجل دخوله فعلاً
     * الملف يُشفَّر بـ AES-256 CBC قبل حفظه على السيرفر.
     *
     * Form-Data:
     * - file: الملف (صورة أو PDF)
     * - note: ملاحظة المريض (اختياري)
     */
    @PostMapping("/{requestId}/upload")
    public ResponseEntity<?> uploadInvestigationResult(
            @PathVariable Long requestId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "note", required = false) String note) {

        if (!isPatient()) {
            return ResponseEntity.status(403).body(Map.of("error", "رفع الملفات مخصص للمرضى فقط."));
        }

        String currentPatient = getCurrentUser();

        // التحقق من وجود الطلب
        Optional<MedicalRequest> optionalRequest = medicalRequestRepository.findById(requestId);
        if (optionalRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MedicalRequest request = optionalRequest.get();

        // 🔒 IDOR Protection: التأكد أن الطلب يخص هذا المريض تحديداً
        if (!request.getPatientNationalId().equals(currentPatient)) {
            return ResponseEntity.status(403).body(Map.of("error", "هذا الطلب لا يخصك."));
        }

        // التحقق من نوع الملف (صور وـ PDF فقط)
        String contentType = file.getContentType();
        if (contentType == null || (!contentType.startsWith("image/") && !contentType.equals("application/pdf"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "نوع الملف غير مدعوم. يُسمح بالصور وملفات PDF فقط."));
        }

        // التحقق من حجم الملف (أقصى 10 ميجابايت)
        if (file.getSize() > 10 * 1024 * 1024) {
            return ResponseEntity.badRequest().body(Map.of("error", "حجم الملف يتجاوز الحد المسموح به (10 ميجابايت)."));
        }

        try {
            byte[] fileBytes = file.getBytes();

            // 🔐 تشفير الملف بـ AES-256 CBC
            byte[] encryptedBytes = fileEncryptionUtil.encrypt(fileBytes);

            // إنشاء مجلد الحفظ إذا لم يكن موجوداً
            Path dir = Paths.get(MEDICAL_FILES_DIR).toAbsolutePath().normalize();
            Files.createDirectories(dir);

            // اسم فريد للملف المشفر (UUID لمنع التخمين)
            String encryptedFileName = UUID.randomUUID().toString() + ".enc";
            Path encryptedFilePath = dir.resolve(encryptedFileName);

            // حفظ الملف المشفر على الهارد
            Files.write(encryptedFilePath, encryptedBytes);

            // لو كان هناك ملف قديم (إعادة رفع) → احذفه لتوفير المساحة
            if (request.getEncryptedFilePath() != null) {
                try {
                    Files.deleteIfExists(Paths.get(request.getEncryptedFilePath()));
                } catch (IOException ex) {
                    System.err.println("⚠️ لم يتم حذف الملف القديم: " + ex.getMessage());
                }
            }

            // تحديث بيانات الطلب في قاعدة البيانات
            request.setEncryptedFilePath(encryptedFilePath.toString());
            request.setFileContentType(contentType);
            request.setIsFulfilled(true);
            request.setFulfilledAt(LocalDateTime.now());
            if (note != null && !note.isBlank()) {
                request.setPatientNote(note.trim());
            }
            medicalRequestRepository.save(request);

            // 🔔 إرسال Push Notification للطبيب
            String requestTypeArabic = "XRAY".equals(request.getRequestType()) ? "الأشعة" : "التحليل";
            try {
                pushNotificationService.sendToUser(
                        request.getDoctorNationalId(),
                        "✅ تم رفع نتيجة " + requestTypeArabic,
                        "المريض " + request.getPatientName() + " رفع نتيجة: " +
                                request.getInvestigationName() + ". افتح ملفه لمراجعتها."
                );
            } catch (Exception e) {
                System.err.println("⚠️ فشل إرسال Push للطبيب: " + e.getMessage());
            }

            return ResponseEntity.ok(Map.of(
                    "message", "تم رفع الملف وتشفيره بنجاح، وتم إشعار الطبيب.",
                    "requestId", requestId,
                    "isFulfilled", true
            ));

        } catch (IOException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "فشل في حفظ الملف: " + e.getMessage()));
        }
    }

    // ====================================================================
    // 📌 5. الطبيب أو المريض: عرض صورة الأشعة (تُفك شفرتها في الذاكرة فقط)
    // ====================================================================

    /**
     * GET /api/medical-requests/image/{requestId}
     * 🔒 الحماية: فقط الطبيب الذي أصدر الطلب أو المريض المعني يمكنه رؤية الصورة
     * الصورة تُفك شفرتها في الـ RAM فقط — السيرفر يحتفظ بها مشفرة دائماً
     */
    @GetMapping("/image/{requestId}")
    public ResponseEntity<byte[]> viewInvestigationImage(@PathVariable Long requestId) {
        String currentUser = getCurrentUser();
        String currentRole = getCurrentRole();

        if (currentUser == null) {
            return ResponseEntity.status(401).build();
        }

        Optional<MedicalRequest> optionalRequest = medicalRequestRepository.findById(requestId);
        if (optionalRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MedicalRequest request = optionalRequest.get();

        // 🔒 IDOR Protection: فقط الطبيب صاحب الطلب أو المريض المعني
        boolean isOwnerDoctor  = "ROLE_DOCTOR".equals(currentRole)  && currentUser.equals(request.getDoctorNationalId());
        boolean isOwnerPatient = "ROLE_PATIENT".equals(currentRole) && currentUser.equals(request.getPatientNationalId());

        if (!isOwnerDoctor && !isOwnerPatient) {
            return ResponseEntity.status(403).build();
        }

        if (!request.getIsFulfilled() || request.getEncryptedFilePath() == null) {
            return ResponseEntity.noContent().build();
        }

        try {
            Path filePath = Paths.get(request.getEncryptedFilePath()).toAbsolutePath().normalize();

            // 🔐 Path Traversal Protection
            Path baseDir = Paths.get(MEDICAL_FILES_DIR).toAbsolutePath().normalize();
            if (!filePath.startsWith(baseDir)) {
                System.out.println("🚨 محاولة Path Traversal محظورة! requestId: " + requestId);
                return ResponseEntity.status(403).build();
            }

            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }

            byte[] encryptedBytes = Files.readAllBytes(filePath);

            // 🔐 فك التشفير في الذاكرة فقط (لا شيء يُكتب على الهارد)
            byte[] decryptedBytes = fileEncryptionUtil.decrypt(encryptedBytes);

            // تحديد نوع المحتوى للمتصفح
            MediaType mediaType = MediaType.IMAGE_JPEG;
            String contentType = request.getFileContentType();
            if (contentType != null) {
                if (contentType.equals("image/png"))             mediaType = MediaType.IMAGE_PNG;
                else if (contentType.equals("image/gif"))        mediaType = MediaType.IMAGE_GIF;
                else if (contentType.equals("application/pdf"))  mediaType = MediaType.APPLICATION_PDF;
            }

            return ResponseEntity.ok()
                    .contentType(mediaType)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                    // منع التخزين المؤقت في المتصفح للحفاظ على الخصوصية
                    .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate")
                    .body(decryptedBytes);

        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ====================================================================
    // 📌 6. الطبيب: حذف طلب (IDOR Protected)
    // ====================================================================

    /**
     * DELETE /api/medical-requests/{requestId}
     * 🔒 الحماية: فقط الطبيب الذي أنشأ الطلب يمكنه حذفه
     */
    @DeleteMapping("/{requestId}")
    public ResponseEntity<?> deleteMedicalRequest(@PathVariable Long requestId) {
        if (!isDoctor()) {
            return ResponseEntity.status(403).body(Map.of("error", "حذف الطلبات مخصص للأطباء فقط."));
        }

        String doctorNationalId = getCurrentUser();

        Optional<MedicalRequest> optionalRequest = medicalRequestRepository.findById(requestId);
        if (optionalRequest.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        MedicalRequest request = optionalRequest.get();

        // 🔒 IDOR Protection: تأكد أن الطبيب هو من أنشأ هذا الطلب
        if (!request.getDoctorNationalId().equals(doctorNationalId)) {
            return ResponseEntity.status(403).body(Map.of("error", "لا يمكنك حذف طلب لا يخصك."));
        }

        // حذف الملف المشفر من الهارد إن وجد
        if (request.getEncryptedFilePath() != null) {
            try {
                Files.deleteIfExists(Paths.get(request.getEncryptedFilePath()));
            } catch (IOException e) {
                System.err.println("⚠️ فشل في حذف ملف الأشعة: " + e.getMessage());
            }
        }

        medicalRequestRepository.deleteById(requestId);

        return ResponseEntity.ok(Map.of("message", "تم حذف الطلب والملف المرتبط به بنجاح."));
    }
}
