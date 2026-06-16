package com.example.demo;

import com.example.demo.domain.PushSubscription;
import com.example.demo.repository.PushSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * ✅ PushSubscriptionController — API لتسجيل/إلغاء المتصفح في قائمة الإشعارات
 */
@CrossOrigin(origins = {"http://localhost:3000", "https://maweed-online.vercel.app"})
@RestController
@RequestMapping("/api/push")
public class PushSubscriptionController {

    @Autowired
    private PushSubscriptionRepository repo;

    /**
     * POST /api/push/subscribe
     * المتصفح بيبعت بياناته بعد ما اليوزر يوافق على الإشعارات
     */
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestHeader("X-National-Id") String nationalId,
            @RequestBody Map<String, Object> body) {
        // 🔐 IDOR Protection: التحقق من أن المستخدم يسجل اشتراكاته بنفسه فقط
        var authCtx = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authCtx != null && !"anonymousUser".equals(authCtx.getPrincipal())) {
            String caller = authCtx.getName();
            if (!caller.equals(nationalId)) {
                return ResponseEntity.status(403).body(Map.of("error", "غير مصرح لك بتسجيل اشتراك لمستخدم آخر."));
            }
        }
        try {
            String endpoint  = (String) body.get("endpoint");
            Map<?, ?> keys   = (Map<?, ?>) body.get("keys");
            String p256dh    = (String) keys.get("p256dh");
            String auth      = (String) keys.get("auth");

            // لو مسجل قبل كده، متضيفهوش تاني
            if (repo.existsByNationalIdAndEndpoint(nationalId, endpoint)) {
                return ResponseEntity.ok(Map.of("message", "already subscribed"));
            }

            PushSubscription sub = new PushSubscription();
            sub.setNationalId(nationalId);
            sub.setEndpoint(endpoint);
            sub.setP256dhKey(p256dh);
            sub.setAuthKey(auth);
            repo.save(sub);

            return ResponseEntity.ok(Map.of("message", "subscribed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/push/unsubscribe
     * لما اليوزر يلغي الإذن
     */
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(@RequestBody Map<String, String> body) {
        String endpoint = body.get("endpoint");
        repo.deleteByEndpoint(endpoint);
        return ResponseEntity.ok(Map.of("message", "unsubscribed"));
    }

    /**
     * GET /api/push/vapid-public-key
     * الفرونت إند محتاج الـ Public Key عشان يعمل Subscribe
     */
    @GetMapping("/vapid-public-key")
    public ResponseEntity<Map<String, String>> getPublicKey(
            @org.springframework.beans.factory.annotation.Value("${vapid.public.key}") String publicKey) {
        return ResponseEntity.ok(Map.of("publicKey", publicKey));
    }
}
