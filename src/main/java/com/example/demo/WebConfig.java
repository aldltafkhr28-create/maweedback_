package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Autowired
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // نطبق حماية الـ Rate Limiting على مسارات الدكاترة لمنع السحب
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/doctors", "/api/doctors/top", "/api/doctors/search-by-code/**");
        
        // يمكنك إضافة أي مسارات أخرى تريد حمايتها هنا مستقبلاً
    }
}
