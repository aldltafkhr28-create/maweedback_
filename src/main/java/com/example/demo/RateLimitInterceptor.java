package com.example.demo;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    // ذاكرة مؤقتة لتخزين الـ Bucket لكل IP
    private final Map<String, Bucket> cache = new ConcurrentHashMap<>();

    private Bucket resolveBucket(String ip) {
        return cache.computeIfAbsent(ip, this::newBucket);
    }

    private Bucket newBucket(String ip) {
        // الحد المسموح: 60 طلب في الدقيقة لكل مستخدم (IP)
        Bandwidth limit = Bandwidth.builder()
                .capacity(60)
                .refillGreedy(60, Duration.ofMinutes(1))
                .build();
        
        return Bucket.builder()
                .addLimit(limit)
                .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getHeader("X-Real-IP");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) ip = request.getRemoteAddr();
        return ip != null ? ip.split(",")[0].trim() : "unknown";
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // تطبيق الحد على طلبات الـ GET فقط (عمليات سحب وعرض البيانات)
        if (request.getMethod().equalsIgnoreCase("GET")) {
            String ip = resolveClientIp(request);
            Bucket bucket = resolveBucket(ip);
            ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
            
            if (probe.isConsumed()) {
                // إرسال عدد الطلبات المتبقية في الهيدر (اختياري للـ Frontend)
                response.addHeader("X-Rate-Limit-Remaining", String.valueOf(probe.getRemainingTokens()));
                return true;
            } else {
                // في حالة تجاوز الحد المسموح (Rate Limit Exceeded)
                long waitForRefill = probe.getNanosToWaitForRefill() / 1_000_000_000;
                response.addHeader("X-Rate-Limit-Retry-After-Seconds", String.valueOf(waitForRefill));
                
                // إرسال خطأ 429 Too Many Requests
                response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
                response.setContentType("application/json;charset=UTF-8");
                response.getWriter().write("{\"error\": \"تم تجاوز الحد المسموح به للطلبات. يرجى الانتظار لمدة " + waitForRefill + " ثانية.\"}");
                return false;
            }
        }
        return true;
    }
}
