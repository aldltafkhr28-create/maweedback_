package com.example.demo;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🔐 JwtFilter — يفحص كل Request ويتحقق من التوكن
 * لو التوكن صحيح → يدخل الـ Security Context
 * لو غلط أو مافيش → يمر (الـ SecurityConfig هيقرر يرفض أو يقبل)
 *
 * ✅ يستخدم Token Cache (30 ثانية) لتقليل الـ DB queries المتكررة
 */
@Component
public class JwtFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @Autowired
    private com.example.demo.repository.DoctorRepository doctorRepository;

    @Autowired
    private com.example.demo.repository.AssistantRequestRepository assistantRepository;

    private static final List<String> ALLOWED_ORIGINS = List.of(
        "http://localhost:3000",
        "https://maweed-online.vercel.app"
    );

    // ✅ Cache: token -> [isActive (0 or 1), expiryTimeMs]
    // بيمنع تكرار الـ DB lookup لنفس التوكن خلال 30 ثانية
    private static final Map<String, long[]> TOKEN_CACHE = new ConcurrentHashMap<>();
    private static final long CACHE_TTL_MS = 30_000L; // 30 ثانية

    private boolean isTokenActiveFromCache(String token, String nationalId, String role) {
        long now = System.currentTimeMillis();

        // إزالة الـ expired entries بشكل خفيف مع كل request
        TOKEN_CACHE.entrySet().removeIf(e -> e.getValue()[1] < now);

        // هل في cache لسه صالح؟
        long[] cached = TOKEN_CACHE.get(token);
        if (cached != null && cached[1] > now) {
            return cached[0] == 1L;
        }

        // مش موجود في الـ cache — اسأل الـ DB مرة واحدة بس
        boolean isActive = false;

        if ("ROLE_PATIENT".equals(role)) {
            var user = userRepository.findByNationalId(nationalId);
            isActive = user.isPresent() && token.equals(user.get().getActiveToken());
        } else if ("ROLE_DOCTOR".equals(role)) {
            var doctor = doctorRepository.findByNationalId(nationalId);
            isActive = doctor.isPresent() && token.equals(doctor.get().getActiveToken());
        } else if ("ROLE_ACCOUNTANT".equals(role)) {
            for (var a : assistantRepository.findByAssistantNationalId(nationalId)) {
                if (token.equals(a.getActiveToken()) && "APPROVED".equals(a.getStatus())) {
                    isActive = true;
                    break;
                }
            }
        }

        // حفظ النتيجة في الـ cache لـ 30 ثانية
        TOKEN_CACHE.put(token, new long[]{isActive ? 1L : 0L, now + CACHE_TTL_MS});
        return isActive;
    }

    /**
     * ✅ امسح التوكن من الـ cache فوراً (بعد logout أو تجديد الـ token)
     */
    public static void invalidateCachedToken(String token) {
        if (token != null) TOKEN_CACHE.remove(token);
    }

    private void banAccount(String nationalId, String role) {
        System.out.println("🚨🚨 حظر فوري للحساب: " + nationalId + " (دور: " + role + ") — محاولة تجاوز الموقع الرسمي!");
        try {
            if ("ROLE_PATIENT".equals(role)) {
                userRepository.findByNationalId(nationalId).ifPresent(user -> {
                    user.setEnabled(false);
                    user.setActiveToken(null);
                    userRepository.save(user);
                    invalidateCachedToken(user.getActiveToken());
                });
            } else if ("ROLE_DOCTOR".equals(role)) {
                doctorRepository.findByNationalId(nationalId).ifPresent(doctor -> {
                    doctor.setEnabled(false);
                    doctor.setActiveToken(null);
                    doctorRepository.save(doctor);
                });
            } else if ("ROLE_ACCOUNTANT".equals(role)) {
                assistantRepository.findByAssistantNationalId(nationalId).forEach(a -> {
                    if ("APPROVED".equals(a.getStatus())) {
                        invalidateCachedToken(a.getActiveToken());
                        a.setActiveToken(null);
                        a.setStatus("REJECTED");
                        assistantRepository.save(a);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("خطأ أثناء الحظر: " + e.getMessage());
        }
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.isTokenValid(token)) {
                    String nationalId = jwtUtil.extractNationalId(token);
                    String role = jwtUtil.extractRole(token);

                    // 🚨 فحص المصدر
                    String origin = request.getHeader("Origin");
                    String referer = request.getHeader("Referer");
                    String uri = request.getRequestURI();
                    boolean isWebSocket = uri.startsWith("/ws-maweed") || uri.startsWith("/api/ws");

                    boolean isValidOrigin = false;
                    if (origin != null) {
                        isValidOrigin = ALLOWED_ORIGINS.contains(origin);
                    } else if (referer != null) {
                        for (String allowed : ALLOWED_ORIGINS) {
                            if (referer.startsWith(allowed)) {
                                isValidOrigin = true;
                                break;
                            }
                        }
                    }
                    // لو مفيش origin ولا referer (Postman) → isValidOrigin = false

                    if (!isWebSocket && !isValidOrigin) {
                        banAccount(nationalId, role);
                        SecurityContextHolder.clearContext();
                        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        response.setContentType("application/json;charset=UTF-8");
                        response.getWriter().write("{\"error\":\"تم حظر حسابك لمحاولة الوصول غير المصرح بها.\"}");
                        return;
                    }

                    // ✅ استخدام الـ Cache — DB بيتكلم بس لو مش موجود في الـ cache
                    boolean isActive = isTokenActiveFromCache(token, nationalId, role);

                    if (isActive) {
                        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                                nationalId, null,
                                List.of(new SimpleGrantedAuthority(role))
                        );
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        System.out.println("🚨 قفل جلسة قديمة للرقم: " + nationalId);
                        SecurityContextHolder.clearContext();
                    }
                }
            } catch (Exception e) {
                // توكن خاطئ — نتجاهله
                SecurityContextHolder.clearContext();
            }
        }

        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Admin endpoints are protected by AdminSecurityFilter — skip JWT for them
        // Skip /error endpoints to prevent masking 500 errors as 403 Forbidden
        return request.getRequestURI().startsWith("/api/admin") || request.getRequestURI().startsWith("/error");
    }
}
