package com.example.demo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** ✅ BCrypt encoder — يُستخدم في تشفير ومقارنة كلمات السر */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ✅ Public — بدون توكن (محدد بدقة)
                .requestMatchers("/api/auth/login").permitAll()
                .requestMatchers("/api/auth/register").permitAll()
                .requestMatchers("/api/auth/logout").permitAll()   // logout يقبل بدون توكن (لو انتهت الجلسة)
                // 🔐 /api/auth/profile/** تتطلب توكن — لا تضعها هنا!

                .requestMatchers("/api/doctors/register").permitAll()
                .requestMatchers("/api/doctors/activate").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/doctors").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/doctors/{id}").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/doctors/search-by-code/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/doctors/*/working-hours").permitAll()
                .requestMatchers("/uploads/**", "/files/**").permitAll()
                .requestMatchers("/ws-maweed/**").permitAll()

                .requestMatchers("/api/admin/**").permitAll() // يُدار بواسطة AdminSecurityFilter المستقل في Order(1)
                .requestMatchers(HttpMethod.GET, "/api/reviews/recent-high").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/doctors/top").permitAll()
                .requestMatchers("/error").permitAll()

                // 🔐 كل الباقي يتطلب JWT صالح
                .anyRequest().authenticated()
            )
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(Arrays.asList("http://localhost:3000", "https://maweed-online.vercel.app"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(Arrays.asList("Authorization", "Content-Type", "X-National-Id", "x-national-id"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}