package com.example.demo;

import com.example.demo.DTO.LoginResponse;
import com.example.demo.DTO.UserDTO;
import com.example.demo.DTO.UserResponseDTO;
import com.example.demo.domain.IpRegistrationLimit;
import com.example.demo.repository.IpRegistrationLimitRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/auth")
public class UserController {

    // 🔐 Rate Limiting: max 20 login attempts per IP per 5 minutes
    private static final ConcurrentHashMap<String, long[]> LOGIN_ATTEMPTS = new ConcurrentHashMap<>();
    private static final int  MAX_ATTEMPTS  = 20;
    private static final long WINDOW_MS     = 5 * 60 * 1000L; // 5 minutes

    private boolean isRateLimited(String ip) {
        long now = System.currentTimeMillis();
        long[] data = LOGIN_ATTEMPTS.compute(ip, (k, v) -> {
            if (v == null || now - v[1] > WINDOW_MS) return new long[]{1, now};
            v[0]++;
            return v;
        });
        return data[0] > MAX_ATTEMPTS;
    }

    @Autowired
    private UserService userService;

    @Autowired
    private IpRegistrationLimitRepository ipRegistrationLimitRepository;

    // ──────────────────────────────────────────────────────────
    /** 🔐 استخراج الـ IP الحقيقي (يتحكم في Proxy/Load Balancer) */
    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getRemoteAddr();
        return ip.split(",")[0].trim(); // لو في load balancer بيبعت قائمة IPs
    }

    /** 🔐 فحص الحد وزيادة العداد (atomic) */
    private static final int MAX_ACCOUNTS_PER_IP = 5;

    /** 🔐 فحص الحد (بدون زيادة) */
    private boolean isIpLimitExceeded(String ip) {
        IpRegistrationLimit record = ipRegistrationLimitRepository
                .findById(ip)
                .orElse(new IpRegistrationLimit(ip));

        if (record.getRegistrationCount() >= MAX_ACCOUNTS_PER_IP) {
            System.out.println("🚨 IP limit exceeded for: " + ip + " (count=" + record.getRegistrationCount() + ")");
            return true; // متجاوز الحد
        }
        return false; // مسموح
    }

    /** 🔐 زيادة العداد بعد نجاح التسجيل */
    private void incrementIpLimit(String ip) {
        IpRegistrationLimit record = ipRegistrationLimitRepository
                .findById(ip)
                .orElse(new IpRegistrationLimit(ip));
        record.setRegistrationCount(record.getRegistrationCount() + 1);
        record.setLastRegisteredAt(LocalDateTime.now());
        ipRegistrationLimitRepository.save(record);
        System.out.println("✅ IP registration count for " + ip + ": " + record.getRegistrationCount() + "/" + MAX_ACCOUNTS_PER_IP);
    }

    /**
     * ✅ تسجيل مريض جديد
     * 🔒 محمي: يرفض أي طلب من خارج الموقع الرسمي (Postman, curl, مواقع أخرى)
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(
            @Valid @RequestBody UserDTO userDTO,
            jakarta.servlet.http.HttpServletRequest request) {
        try {
            // 🔒 فحص المصدر
            String origin = request.getHeader("Origin");
            String referer = request.getHeader("Referer");
            boolean isValidSource = false;
            if (origin != null && (
                    origin.equals("http://localhost:3000") ||
                    origin.equals("https://maweed-online.vercel.app"))) {
                isValidSource = true;
            } else if (referer != null && (
                    referer.startsWith("http://localhost:3000") ||
                    referer.startsWith("https://maweed-online.vercel.app"))) {
                isValidSource = true;
            }
            if (!isValidSource) {
                System.out.println("🚨 محاولة تسجيل من مصدر غير مصرح به! Origin: " + origin + ", Referer: " + referer);
                return ResponseEntity.status(403).body("غير مصرح. يجب التسجيل من الموقع الرسمي فقط.");
            }

            // 🔐 فحص حد التسجيل من نفس الجهاز (5 حسابات كحد أقصى)
            String clientIp = resolveClientIp(request);
            if (isIpLimitExceeded(clientIp)) {
                return ResponseEntity.status(429).body(
                    "تم الوصول للحد الأقصى للحسابات المسجّلة من هذا الجهاز (" + MAX_ACCOUNTS_PER_IP + " حسابات كحد أقصى). " +
                    "للاستفسار تواصل مع إدارة الموقع."
                );
            }

            User savedAccount = userService.registerPatient(userDTO);

            // ✅ التسجيل نجح، زوّد عداد الـ IP
            incrementIpLimit(clientIp);

            return ResponseEntity.ok(UserResponseDTO.from(savedAccount));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    /**
     * ✅ ميثود اللوجن الموحدة (مرضى ودكاترة ومساعدين)
     * 🔐 UserService يتولى: التحقق من كلمة السر + توليد JWT + حفظ activeToken
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody UserDTO loginDto,
                                   jakarta.servlet.http.HttpServletRequest request) {
        
        // 🔒 فحص المصدر (Origin/Referer) لمنع تسجيل الدخول من برامج خارجية مثل Postman
        String origin = request.getHeader("Origin");
        String referer = request.getHeader("Referer");
        boolean isValidSource = false;
        if (origin != null && (
                origin.equals("http://localhost:3000") ||
                origin.equals("https://maweed-online.vercel.app"))) {
            isValidSource = true;
        } else if (referer != null && (
                referer.startsWith("http://localhost:3000") ||
                referer.startsWith("https://maweed-online.vercel.app"))) {
            isValidSource = true;
        }
        if (!isValidSource) {
            System.out.println("🚨 محاولة تسجيل دخول من مصدر غير مصرح به! Origin: " + origin + ", Referer: " + referer);
            return ResponseEntity.status(403).body("غير مصرح. يجب الدخول من الموقع الرسمي فقط.");
        }

        // 🔐 Rate Limiting
        String ip = resolveClientIp(request); // استخدام الدالة الموحدة الموثوقة بدلا من تكرار الكود
        if (isRateLimited(ip)) {
            System.out.println("🚨 Rate limit exceeded for IP: " + ip);
            return ResponseEntity.status(429).body("تجاوزت عدد محاولات تسجيل الدخول. حاول مرة أخرى بعد 15 دقيقة.");
        }
        try {
            // ✅ UserService يفعل كل شيء: التحقق، توليد Token، حفظه في DB
            LoginResponse response = userService.login(loginDto.getNationalId(), loginDto.getPassword());

            if (response != null) {
                // ✅ تسجيل الدخول نجح — صفِّر عداد المحاولات عشان لو عاد الدكتور بنفس المحطة
                LOGIN_ATTEMPTS.remove(ip);
                return ResponseEntity.ok(response);
            }
            return ResponseEntity.status(401).body("الرقم القومي أو كلمة المرور غير صحيحة");
        } catch (RuntimeException e) {
            // حساب موقوف أو دخول متزامن من جهاز آخر
            return ResponseEntity.status(401).body(e.getMessage());
        }
    }

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;
    
    @Autowired
    private com.example.demo.repository.DoctorRepository doctorRepository;
    
    @Autowired
    private com.example.demo.repository.AssistantRequestRepository assistantRepository;

    @Autowired
    private com.example.demo.repository.AppointmentRepository appointmentRepository;

    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        var authentication = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok("Already logged out.");
        }

        String nationalId = authentication.getName();
        String role = authentication.getAuthorities().iterator().next().getAuthority();

        if (role.equals("ROLE_PATIENT")) {
            var user = userRepository.findByNationalId(nationalId);
            if (user.isPresent()) {
                user.get().setActiveToken(null);
                userRepository.save(user.get());
            }
        } else if (role.equals("ROLE_DOCTOR")) {
            var doctor = doctorRepository.findByNationalId(nationalId);
            if (doctor.isPresent()) {
                doctor.get().setActiveToken(null);
                doctorRepository.save(doctor.get());
            }
        } else if (role.equals("ROLE_ACCOUNTANT")) {
            java.util.List<com.example.demo.domain.AssistantRequest> assistants = assistantRepository.findByAssistantNationalId(nationalId);
            if (assistants != null) {
                for (com.example.demo.domain.AssistantRequest a : assistants) {
                    if ("APPROVED".equals(a.getStatus())) {
                        a.setActiveToken(null);
                        assistantRepository.save(a);
                    }
                }
            }
        }

        return ResponseEntity.ok("تم تسجيل الخروج بنجاح.");
    }

    /**
     * ✅ جلب الملف الطبي للمريض — المستخدم نفسه أو طبيب/مساعد (حماية IDOR)
     */
    @GetMapping("/profile/{nationalId}")
    public ResponseEntity<?> getUserProfile(@PathVariable String nationalId) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String callerRole = auth != null && !auth.getAuthorities().isEmpty()
                ? auth.getAuthorities().iterator().next().getAuthority() : null;
        String callerId = auth != null ? auth.getName() : null;

        // 🔐 فقط صاحب الحساب أو طبيب/مساعد مرتبط بالمريض
        boolean isOwner = nationalId.equals(callerId);
        boolean isAuthorizedDoctorOrAssistant = false;

        if ("ROLE_DOCTOR".equals(callerRole) || "ROLE_ACCOUNTANT".equals(callerRole)) {
            String doctorIdToCheck = callerId;
            if ("ROLE_ACCOUNTANT".equals(callerRole)) {
                // البحث عن الطبيب المرتبط بهذا المساعد
                java.util.Optional<com.example.demo.domain.AssistantRequest> approvedRequest = assistantRepository.findByAssistantNationalId(callerId)
                        .stream()
                        .filter(a -> "APPROVED".equals(a.getStatus()))
                        .findFirst();
                if (approvedRequest.isPresent()) {
                    doctorIdToCheck = approvedRequest.get().getDoctorNationalId();
                } else {
                    doctorIdToCheck = null; // مساعد غير معتمد
                }
            }

            // التحقق مما إذا كان هناك حجز مسبق بين هذا الطبيب وهذا المريض
            if (doctorIdToCheck != null) {
                boolean hasAppointment = appointmentRepository.findByDoctorNationalId(doctorIdToCheck).stream()
                        .anyMatch(app -> nationalId.equals(app.getPatientNationalId()));
                if (hasAppointment) {
                    isAuthorizedDoctorOrAssistant = true;
                }
            }
        }

        if (!isOwner && !isAuthorizedDoctorOrAssistant) {
            return ResponseEntity.status(403).body("غير مصرح لك بالاطلاع على بيانات هذا المريض.");
        }

        java.util.Optional<User> userOptional = userRepository.findByNationalId(nationalId);
        if (userOptional.isPresent()) {
            return ResponseEntity.ok(UserResponseDTO.from(userOptional.get()));
        }
        return ResponseEntity.status(404).body("المريض غير موجود");
    }

    /**
     * ✅ تحديث الملف الطبي — صاحب الحساب فقط (حماية IDOR)
     */
    @PutMapping("/profile/{nationalId}")
    public ResponseEntity<?> updateUserProfile(@PathVariable String nationalId,
                                               @RequestBody com.example.demo.DTO.UserProfileDTO profileDTO) {
        // 🔐 فقط صاحب الحساب نفسه يقدر يعدّل بياناته
        var auth = SecurityContextHolder.getContext().getAuthentication();
        String callerId = auth != null ? auth.getName() : null;
        if (!nationalId.equals(callerId)) {
            return ResponseEntity.status(403).body("غير مصرح لك بتعديل بيانات مستخدم آخر.");
        }

        java.util.Optional<User> userOptional = userRepository.findByNationalId(nationalId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            user.setBloodType(profileDTO.getBloodType());
            user.setWeight(profileDTO.getWeight());
            user.setHeight(profileDTO.getHeight());
            user.setAge(profileDTO.getAge());
            user.setChronicDiseases(profileDTO.getChronicDiseases());
            user.setAllergies(profileDTO.getAllergies());
            userRepository.save(user);
            return ResponseEntity.ok(UserResponseDTO.from(user));
        }
        return ResponseEntity.status(404).body("المريض غير موجود");
    }
}