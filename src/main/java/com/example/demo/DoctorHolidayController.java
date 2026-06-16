package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/doctors/{nationalId}/holiday")
public class DoctorHolidayController {

    @Autowired
    private DoctorService doctorService;

    @Autowired
    private com.example.demo.repository.AssistantRequestRepository assistantRequestRepository;
    
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
    
    private boolean isAuthorizedForDoctor(String doctorNationalId) {
        String role = getCurrentRole();
        String user = getCurrentUser();
        if (role == null || user == null) return false;
        if ("ROLE_DOCTOR".equals(role)) return user.equals(doctorNationalId);
        if ("ROLE_ACCOUNTANT".equals(role)) {
            return assistantRequestRepository.findByAssistantNationalId(user).stream()
                    .anyMatch(a -> a.getDoctorNationalId().equals(doctorNationalId) && "APPROVED".equals(a.getStatus()));
        }
        return false;
    }

    @PostMapping("/sudden")
    public ResponseEntity<?> applySuddenHoliday(@PathVariable String nationalId, @RequestBody Map<String, String> payload) {
        if (!isAuthorizedForDoctor(nationalId)) {
            return ResponseEntity.status(403).body("غير مصرح لك بإضافة إجازة لهذا الطبيب.");
        }
        try {
            String dateStr = payload.get("offDate");
            if (dateStr == null || dateStr.isEmpty()) {
                return ResponseEntity.badRequest().body("يجب إرسال تاريخ الإجازة (offDate).");
            }
            LocalDate offDate = LocalDate.parse(dateStr);
            int count = doctorService.applySuddenHoliday(nationalId, offDate);
            return ResponseEntity.ok(count + "");
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("تعذر تنفيذ الخوارزمية: " + e.getMessage());
        }
    }
}
