package com.example.demo;

import com.example.demo.repository.AppointmentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * ✅ AppointmentReminderScheduler
 * بيشتغل كل دقيقة ويبعت تذكير للمرضى اللي موعدهم بعد 20 دقيقة
 * عبر Web Push
 */
@Component
public class AppointmentReminderScheduler {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private com.example.demo.repository.UserRepository userRepository;

    @Autowired
    private PushNotificationService pushService;

    @Scheduled(fixedRate = 60_000) // كل دقيقة
    public void sendAppointmentReminders() {
        // ✅ استخدام توقيت القاهرة (UTC+3) بدلاً من UTC
        ZoneId cairoZone = ZoneId.of("Africa/Cairo");
        LocalDate today = LocalDate.now(cairoZone);
        LocalTime now   = LocalTime.now(cairoZone);

        // نافذة الـ 20 دقيقة: من 19 لـ 21 دقيقة قدام
        LocalTime from = now.plusMinutes(19);
        LocalTime to   = now.plusMinutes(21);

        List<com.example.demo.Appointment> upcoming =
            appointmentRepository.findRemindersToSend(today, from, to);

        for (com.example.demo.Appointment appt : upcoming) {
            try {
                // تنسيق الوقت بشكل مقروء
                int h  = appt.getAppointmentTime().getHour();
                int m  = appt.getAppointmentTime().getMinute();
                String ampm = h < 12 ? "ص" : "م";
                int h12 = h == 0 ? 12 : h > 12 ? h - 12 : h;
                String timeStr = String.format("%d:%02d %s", h12, m, ampm);
                String title   = "⏰ تذكير: موعدك بعد 20 دقيقة!";
                String body    = "موعدك مع " + appt.getDoctorName() + " الساعة " + timeStr;

                // Web Push (للمتصفح)
                pushService.sendToUser(appt.getPatientNationalId(), title, body);

                // ضع علامة إن الرسالة اتبعتت
                appt.setReminderSent(true);
                appointmentRepository.save(appt);
                System.out.println("✅ Reminder sent (Push) to: " + appt.getPatientNationalId());

            } catch (Exception e) {
                System.err.println("⚠️ خطأ في إرسال تذكير الموعد: " + e.getMessage());
            }
        }
    }

    /**
     * ✅ عملية تسجيل الغياب التلقائي (Automated No-Show)
     * تشتغل الساعة 3:00 الفجر يومياً لتعالج المواعيد اللي محدش حضرها في الأيام السابقة
     */
    @Scheduled(cron = "0 0 3 * * ?", zone = "Africa/Cairo")
    public void processDailyNoShows() {
        ZoneId cairoZone = ZoneId.of("Africa/Cairo");
        LocalDate today = LocalDate.now(cairoZone);

        // جلب كل المواعيد اللي كانت قبل النهاردة ولسه حالتها معلقة (محدش قفلها ولا أثبت حضورها)
        List<com.example.demo.Appointment> missedAppointments = appointmentRepository.findPastUncompletedAppointments(today);

        for (com.example.demo.Appointment appt : missedAppointments) {
            try {
                // 1. تغيير حالة الموعد لـ غياب
                appt.setStatus("NO_SHOW");
                appointmentRepository.save(appt);

                // 2. تحديث سجل المريض
                userRepository.findByNationalId(appt.getPatientNationalId()).ifPresent(user -> {
                    user.setNoShowCount(user.getNoShowCount() + 1);
                    
                    String title = "⚠️ إنذار غياب";
                    String body = "تم تسجيل غيابك عن موعدك السابق مع " + appt.getDoctorName() + ". نرجو الالتزام بالمواعيد.";

                    // حظر الحساب إذا وصلت الغيابات لـ 3
                    if (user.getNoShowCount() >= 3) {
                        user.setEnabled(false); // 🚫 الحظر
                        title = "🚫 تم إيقاف حسابك";
                        body = "تم إيقاف حسابك لتجاوز الحد الأقصى للغياب بدون إلغاء (3 مرات). يرجى التواصل مع الدعم.";
                    }
                    
                    userRepository.save(user);

                    // 3. إرسال إشعار للمريض
                    pushService.sendToUser(user.getNationalId(), title, body);
                    System.out.println("✅ Automated NO_SHOW applied to patient: " + user.getNationalId() + " | Warning count: " + user.getNoShowCount());
                });

            } catch (Exception e) {
                System.err.println("⚠️ خطأ في معالجة غياب تلقائي للموعد رقم " + appt.getId() + ": " + e.getMessage());
            }
        }
    }
}
