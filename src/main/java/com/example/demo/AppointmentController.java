package com.example.demo;

import com.example.demo.domain.WorkingHour;
import com.example.demo.repository.AppointmentRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.WorkingHourRepository;
import com.example.demo.repository.DoctorRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/appointments")
public class AppointmentController {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private WorkingHourRepository workingHourRepository;

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private PushNotificationService pushNotificationService;

    // الحد الأقصى للوقت بين الحجوزات (20 دقيقة)
    private static final int MIN_GAP_MINUTES = 20;

    // تحويل يوم الأسبوع الإنجليزي إلى العربي (لمطابقة قاعدة البيانات)
    private String toArabicDay(DayOfWeek dow) {
        switch (dow) {
            case SATURDAY:  return "السبت";
            case SUNDAY:    return "الأحد";
            case MONDAY:    return "الاثنين";
            case TUESDAY:   return "الثلاثاء";
            case WEDNESDAY: return "الأربعاء";
            case THURSDAY:  return "الخميس";
            case FRIDAY:    return "الجمعة";
            default:        return "";
        }
    }

    /**
     * Helper to get current principal
     */
    private String getCurrentUser() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        return (auth != null && !auth.getPrincipal().equals("anonymousUser")) ? auth.getName() : null;
    }

    private String getCurrentRole() {
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && !auth.getAuthorities().isEmpty() && !auth.getPrincipal().equals("anonymousUser")) {
            return auth.getAuthorities().iterator().next().getAuthority();
        }
        return null;
    }

    @Autowired
    private com.example.demo.repository.AssistantRequestRepository assistantRequestRepository;

    private boolean isAuthorizedForDoctor(String doctorNationalId) {
        String role = getCurrentRole();
        String user = getCurrentUser();
        if (role == null || user == null) return false;
        
        if ("ROLE_DOCTOR".equals(role)) {
            return user.equals(doctorNationalId);
        }
        if ("ROLE_ACCOUNTANT".equals(role)) {
            return assistantRequestRepository.findByAssistantNationalId(user).stream()
                    .anyMatch(a -> a.getDoctorNationalId().equals(doctorNationalId) && "APPROVED".equals(a.getStatus()));
        }
        return false;
    }

    /**
     * 1. حجز موعد جديد (من قبل المريض)
     */
    @PostMapping("/book")
    public ResponseEntity<?> bookAppointment(@RequestBody Appointment appointment, @RequestParam(required = false) Long doctorId, jakarta.servlet.http.HttpServletRequest request) {
        try {
            // حماية الـ Endpoint: التأكد من أن الطلب قادم من الموقع الرسمي فقط وليس من Postman أو أي سيرفر آخر
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
            
            boolean isValidSource = false;
            if (origin != null && (origin.equals("http://localhost:3000") || origin.equals("https://maweed-online.vercel.app"))) {
                isValidSource = true;
            } else if (referer != null && (referer.startsWith("http://localhost:3000") || referer.startsWith("https://maweed-online.vercel.app"))) {
                isValidSource = true;
            }

            if (!isValidSource) {
                System.out.println("🚨 تم حظر محاولة حجز خارجية! Origin: " + origin + ", Referer: " + referer);
                return ResponseEntity.status(403).body("غير مصرح لك بإجراء الحجز. يجب أن يتم الحجز من خلال الموقع الرسمي فقط.");
            }

            if (doctorId != null) {
                Optional<Doctor> optDoc = doctorRepository.findById(doctorId);
                if (optDoc.isPresent()) {
                    appointment.setDoctorNationalId(optDoc.get().getNationalId());
                    if (optDoc.get().getNameDoctor() != null) {
                        appointment.setDoctorName(optDoc.get().getNameDoctor());
                    }
                }
            }

            // تنظيف الأرقام القومية من أي مسافات
            if (appointment.getDoctorNationalId() != null)
                appointment.setDoctorNationalId(appointment.getDoctorNationalId().trim());
            if (appointment.getPatientNationalId() != null)
                appointment.setPatientNationalId(appointment.getPatientNationalId().trim());

            // 🔒 التحقق من الهوية (IDOR Protection)
            String curRole = getCurrentRole();
            String curUser = getCurrentUser();
            if ("ROLE_PATIENT".equals(curRole) && !curUser.equals(appointment.getPatientNationalId())) {
                return ResponseEntity.status(403).body("لا يمكنك حجز موعد لمريض آخر.");
            }

            // 1. فحص قاعدة: ميعاد واحد للمريض مع نفس الدكتور خلال 48 ساعة من وقت الحجز (للمريض فقط)
            if (!"ROLE_DOCTOR".equals(curRole)) {
                LocalDateTime now = LocalDateTime.now(ZoneId.of("Africa/Cairo"));
                LocalDateTime since48h = now.minusHours(48);

                List<Appointment> recentBookings = appointmentRepository
                        .findRecentActiveBookings(
                                appointment.getPatientNationalId(),
                                appointment.getDoctorNationalId(),
                                since48h
                        );

                if (!recentBookings.isEmpty()) {
                    return ResponseEntity.badRequest().body(
                            "لقد قمت بحجز موعد مع هذا الطبيب مسبقاً. لا يمكنك حجز أكثر من موعد واحد لنفس الطبيب خلال 48 ساعة من تاريخ الحجز."
                    );
                }
            }

            // 2. فحص التعارض الزمني للدكتور (أقل من 20 دقيقة)
            List<Appointment> existingAppointments = appointmentRepository
                    .findByDoctorNationalIdAndAppointmentDate(
                            appointment.getDoctorNationalId(),
                            appointment.getAppointmentDate()
                    );

            LocalTime newTime = appointment.getAppointmentTime();

            // فحص التعارض: السماح لمريضين كحد أقصى في نفس الموعد
            int overlapCount = 0;
            for (Appointment existing : existingAppointments) {
                if ("REJECTED".equals(existing.getStatus()) || "CANCELLED".equals(existing.getStatus())) {
                    continue;
                }

                LocalTime existingTime = existing.getAppointmentTime();
                long diffMinutes = Math.abs(
                        newTime.toSecondOfDay() - existingTime.toSecondOfDay()
                ) / 60;

                if (diffMinutes < MIN_GAP_MINUTES) {
                    overlapCount++;
                }
            }

            if (overlapCount >= 2) {
                return ResponseEntity.badRequest().body(
                        "عفواً، اكتمل العدد الأقصى لهذا الموعد (مريضين كحد أقصى)."
                );
            }

            // 3. فحص أيام الإجازة: تأكد أن اليوم المختار ليس يوم إجازة للدكتور
            String doctorNatId = appointment.getDoctorNationalId();
            
            // تحقق أولاً من الإجازات الطارئة (الاستثنائية)
            Optional<Doctor> checkDoctorOpt = doctorRepository.findByNationalId(doctorNatId);
            if (checkDoctorOpt.isPresent() && checkDoctorOpt.get().getBlockedDates() != null) {
                if (checkDoctorOpt.get().getBlockedDates().contains(appointment.getAppointmentDate().toString())) {
                    return ResponseEntity.badRequest().body("عفواً، الطبيب في إجازة طارئة يوم " + appointment.getAppointmentDate() + ". يرجى اختيار يوم آخر.");
                }
            }

            List<WorkingHour> workingHours = workingHourRepository.findByDoctorNationalId(doctorNatId);
            if (!workingHours.isEmpty()) {
                String arabicDay = toArabicDay(appointment.getAppointmentDate().getDayOfWeek());
                boolean isOffDay = workingHours.stream()
                        .anyMatch(wh -> wh.getDayOfWeek().equals(arabicDay) && 
                            (wh.isOff() || (wh.getStartTime().equals(LocalTime.MIDNIGHT) && wh.getEndTime().equals(LocalTime.MIDNIGHT))));
                if (isOffDay) {
                    return ResponseEntity.badRequest().body(
                            "عفواً، الطبيب في إجازة أسبوعية يوم " + arabicDay + ". يرجى اختيار يوم عمل آخر."
                    );
                }
            }

            Appointment saved = appointmentRepository.save(appointment);
            
            // بث الإشعار عبر الـ WebSocket
            messagingTemplate.convertAndSend("/topic/appointments/" + saved.getDoctorNationalId(), "NEW_APPOINTMENT");

            System.out.println("✅ Appointment saved: doctorNationalId=" + saved.getDoctorNationalId() + ", patient=" + saved.getPatientNationalId());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("خطأ في تسجيل الحجز: " + e.getMessage());
        }
    }

    /**
     * 2. جلب حجوزات الطبيب (للوحة تحكم الطبيب)
     */
    @GetMapping("/doctor/{nationalId}")
    public ResponseEntity<?> getDoctorAppointments(@PathVariable String nationalId) {
        String trimmedId = nationalId.trim();
        String curRole = getCurrentRole();
        String curUser = getCurrentUser();

        // 🔑 مريض مسجل دخول يجلب بيانات مجهولة (بدون كشف بيانات المرضى)
        if ("ROLE_PATIENT".equals(curRole)) {
            List<Appointment> all = appointmentRepository.findByDoctorNationalId(trimmedId);
            // إخفاء بيانات المريضين الآخرين من المريض الحالي
            String myId = curUser;
            List<java.util.Map<String, Object>> anonymized = all.stream().map(a -> {
                java.util.Map<String, Object> m = new java.util.HashMap<>();
                m.put("appointmentDate", a.getAppointmentDate());
                m.put("appointmentTime", a.getAppointmentTime());
                m.put("status", a.getStatus());
                m.put("visitType", a.getVisitType());
                // فقط نعطي الرقم القومي للمريض نفسه حتى يفيلتر مواعيده من بين الكل
                if (myId != null && myId.equals(a.getPatientNationalId())) {
                    m.put("patientNationalId", a.getPatientNationalId());
                    m.put("id", a.getId());
                } else {
                    m.put("patientNationalId", null);
                    m.put("id", null);
                }
                return m;
            }).collect(Collectors.toList());
            return ResponseEntity.ok(anonymized);
        }

        // 🔒 التحقق من الهوية للطبيب والمساعد (IDOR Protection)
        if (!isAuthorizedForDoctor(trimmedId)) {
            return ResponseEntity.status(403).build();
        }

        System.out.println("📋 Fetching appointments for doctorNationalId: [" + trimmedId + "]");
        List<Appointment> appointments = appointmentRepository.findByDoctorNationalId(trimmedId);
        System.out.println("📋 Found: " + appointments.size() + " appointments");
        return ResponseEntity.ok(appointments);
    }

    /**
     * 3. جلب حجوزات المريض (للوحة تحكم المريض - حجوزاتي)
     */
    @GetMapping("/patient/{nationalId}")
    public ResponseEntity<List<Appointment>> getPatientAppointments(@PathVariable String nationalId) {
        // 🔒 التحقق من الهوية (IDOR Protection)
        if ("ROLE_PATIENT".equals(getCurrentRole()) && !nationalId.equals(getCurrentUser())) {
            return ResponseEntity.status(403).build();
        }

        List<Appointment> appointments = appointmentRepository.findByPatientNationalId(nationalId);
        return ResponseEntity.ok(appointments);
    }

    /**
     * 4. تغيير حالة الحجز (مقبول / مرفوض) من لوحة الطبيب
     */
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        Optional<Appointment> optionalAppointment = appointmentRepository.findById(id);
        if (optionalAppointment.isPresent()) {
            Appointment appointment = optionalAppointment.get();

            String newStatus = payload.get("status"); // PENDING, APPROVED, REJECTED

            // 🔒 التحقق من الهوية
            String curRole = getCurrentRole();
            String curUser = getCurrentUser();
            boolean isDoctorOrAssistant = (appointment.getDoctorNationalId() != null && isAuthorizedForDoctor(appointment.getDoctorNationalId().trim()));
            boolean isPatientMarkingDone = ("ROLE_PATIENT".equals(curRole) && curUser != null && curUser.equals(appointment.getPatientNationalId()) && "DONE".equalsIgnoreCase(newStatus));

            if (!isDoctorOrAssistant && !isPatientMarkingDone) {
                return ResponseEntity.status(403).body("غير مصرح لك بتعديل حالة حجز لطبيب آخر.");
            }
            if (newStatus != null) {
                String upperStatus = newStatus.toUpperCase();
                if (upperStatus.equals("PENDING") || upperStatus.equals("APPROVED") || upperStatus.equals("REJECTED") || upperStatus.equals("DONE") || upperStatus.equals("NO_SHOW") || upperStatus.equals("ARRIVED")) {
                    appointment.setStatus(upperStatus);
                    appointmentRepository.save(appointment);

                    // 🚨 زيادة عدد الغيابات إذا كانت الحالة NO_SHOW
                    if (upperStatus.equals("NO_SHOW")) {
                        userRepository.findByNationalId(appointment.getPatientNationalId()).ifPresent(user -> {
                            user.setNoShowCount(user.getNoShowCount() + 1);
                            if (user.getNoShowCount() >= 3) {
                                user.setEnabled(false); // 🚫 حظر المريض بعد 3 غيابات
                            }
                            userRepository.save(user);
                        });
                    }

                    // بث التحديث عبر الـ WebSocket
                    messagingTemplate.convertAndSend("/topic/appointments/" + appointment.getDoctorNationalId(), "UPDATE_APPOINTMENT");

                    // 🔔 إرسال إشعار للمريض عند الحضور (DONE) أو الإلغاء/الرفض (REJECTED/NO_SHOW)
                    if (upperStatus.equals("DONE")) {
                        try {
                            pushNotificationService.sendToUser(
                                appointment.getPatientNationalId(),
                                "✅ إتمام الكشف",
                                "تم إتمام الكشف بنجاح مع د. " + appointment.getDoctorName() + ". نتمنى لك الشفاء العاجل!"
                            );
                        } catch(Exception e) {
                            System.err.println("خطأ أثناء إرسال الإشعار: " + e.getMessage());
                        }
                    } else if (upperStatus.equals("REJECTED") || upperStatus.equals("NO_SHOW")) {
                        try {
                            pushNotificationService.sendToUser(
                                appointment.getPatientNationalId(),
                                "❌ تم إلغاء موعدك",
                                "عفواً، تم إغلاق أو إلغاء موعدك مع د. " + appointment.getDoctorName() + " يوم " + appointment.getAppointmentDate() + "."
                            );
                        } catch(Exception e) {
                            System.err.println("خطأ أثناء إرسال الإشعار: " + e.getMessage());
                        }
                    }

                    return ResponseEntity.ok(appointment);
                } else {
                    return ResponseEntity.badRequest().body("حالة الحجز غير مدعومة");
                }
            }
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * تحديث المبلغ المدفوع في الحجز (أقساط عمليات أو غيره)
     */
    @PutMapping("/{id}/pay")
    public ResponseEntity<?> addPaymentToAppointment(@PathVariable Long id, @RequestBody Map<String, Double> payload) {
        Optional<Appointment> optionalAppointment = appointmentRepository.findById(id);
        if (optionalAppointment.isPresent()) {
            Appointment appointment = optionalAppointment.get();

            // 🔒 التحقق من الهوية
            if (appointment.getDoctorNationalId() != null && !isAuthorizedForDoctor(appointment.getDoctorNationalId().trim())) {
                return ResponseEntity.status(403).body("غير مصرح لك بتسجيل أموال لطبيب آخر.");
            }

            Double amount = payload.get("paidAmount");
            if (amount != null) {
                // إذا كان هناك مبلغ مدفوع مسبقاً في نفس الموعد، نزيد عليه (في حالة دفع مرتين في نفس الجلسة)
                Double currentPaid = appointment.getPaidAmount() != null ? appointment.getPaidAmount() : 0.0;
                appointment.setPaidAmount(currentPaid + amount);
                appointmentRepository.save(appointment);
                
                // بث التحديث عبر الـ WebSocket لتحديث أرباح اليوم مباشرة
                messagingTemplate.convertAndSend("/topic/appointments/" + appointment.getDoctorNationalId(), "UPDATE_APPOINTMENT");
                
                return ResponseEntity.ok(appointment);
            }
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * الحصول على دور المريض الحالي (Live Queue Tracker)
     */
    @GetMapping("/{id}/queue-status")
    public ResponseEntity<?> getQueueStatus(@PathVariable Long id) {
        Optional<Appointment> opt = appointmentRepository.findById(id);
        if (!opt.isPresent()) return ResponseEntity.notFound().build();
        Appointment appointment = opt.get();

        // 🔒 التحقق من الهوية
        String curRole = getCurrentRole();
        String curUser = getCurrentUser();
        if ("ROLE_PATIENT".equals(curRole) && !curUser.equals(appointment.getPatientNationalId())) {
            return ResponseEntity.status(403).body("غير مصرح لك");
        }

        // المتتبع يعمل فقط لمواعيد اليوم
        if (!appointment.getAppointmentDate().equals(LocalDate.now(ZoneId.of("Africa/Cairo")))) {
            return ResponseEntity.badRequest().body("متتبع الدور متاح فقط لمواعيد اليوم");
        }

        // جلب جميع مواعيد الطبيب في نفس اليوم
        List<Appointment> todaysAppointments = appointmentRepository.findByDoctorNationalIdAndAppointmentDate(
                appointment.getDoctorNationalId(), LocalDate.now(ZoneId.of("Africa/Cairo"))
        );

        // تصفية المواعيد النشطة فقط
        List<Appointment> activeQueue = todaysAppointments.stream()
                .filter(a -> "PENDING".equals(a.getStatus()) || "APPROVED".equals(a.getStatus()) || "ARRIVED".equals(a.getStatus()))
                .sorted((a1, a2) -> a1.getAppointmentTime().compareTo(a2.getAppointmentTime()))
                .collect(Collectors.toList());

        int position = -1;
        for (int i = 0; i < activeQueue.size(); i++) {
            if (activeQueue.get(i).getId().equals(id)) {
                position = i + 1;
                break;
            }
        }

        if (position == -1) {
            // المريض غير موجود في قائمة الانتظار (ربما تم الانتهاء من كشفه أو تم رفضه)
            return ResponseEntity.ok(Map.of("position", 0, "estimatedWaitMinutes", 0)); 
        }

        int estimatedWaitMinutes = (position - 1) * 20; // 20 دقيقة متوسط لكل مريض يسبقه

        return ResponseEntity.ok(Map.of(
            "position", position,
            "estimatedWaitMinutes", estimatedWaitMinutes
        ));
    }

    /**
     * 5. حذف حجز
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAppointment(@PathVariable Long id) {
        Optional<Appointment> opt = appointmentRepository.findById(id);
        if (opt.isPresent()) {
            Appointment appointment = opt.get();

            // 🔒 التحقق من الهوية
            String curRole = getCurrentRole();
            String curUser = getCurrentUser();
            
            if ("ROLE_PATIENT".equals(curRole) && !curUser.equals(appointment.getPatientNationalId())) {
                return ResponseEntity.status(403).body("لا يمكنك حذف حجز لا يخصك.");
            }
            if (appointment.getDoctorNationalId() != null && ("ROLE_DOCTOR".equals(curRole) || "ROLE_ACCOUNTANT".equals(curRole)) && !isAuthorizedForDoctor(appointment.getDoctorNationalId().trim())) {
                return ResponseEntity.status(403).body("لا يمكنك حذف حجز لا يخصك.");
            }

            // إرسال إشعار للمريض قبل الحذف
            try {
                pushNotificationService.sendToUser(
                    appointment.getPatientNationalId(),
                    "❌ تم إلغاء الحجز",
                    "تم إلغاء حجزك مع د. " + appointment.getDoctorName() + " يوم " + appointment.getAppointmentDate() + ". يرجى التواصل أو حجز موعد بديل."
                );
            } catch(Exception e) {
                System.err.println("خطأ أثناء إرسال الإشعار قبل الحذف: " + e.getMessage());
            }

            appointmentRepository.deleteById(id);
            // بث التحديث عبر الـ WebSocket
            messagingTemplate.convertAndSend("/topic/appointments/" + appointment.getDoctorNationalId(), "DELETE_APPOINTMENT");
            return ResponseEntity.ok("تم حذف الحجز بنجاح");
        }
        return ResponseEntity.notFound().build();
    }
    private boolean apartmentNationalIdMatchesDoctor(Appointment appointment, String doctorId) {
        if (appointment.getDoctorNationalId() == null) return false;
        return appointment.getDoctorNationalId().trim().equals(doctorId);
    }
}

