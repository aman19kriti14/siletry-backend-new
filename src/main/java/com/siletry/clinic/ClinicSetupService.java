package com.siletry.clinic;

import com.siletry.qr.QrService;
import com.siletry.template.MessageTemplate;
import com.siletry.template.MessageTemplateRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Everything a new clinic gets on signup. */
@Service
public class ClinicSetupService {

    private final QrService qr;
    private final MessageTemplateRepository templates;
    private final int defaultCap;

    public ClinicSetupService(QrService qr, MessageTemplateRepository templates,
                              @Value("${siletry.default-message-cap}") int defaultCap) {
        this.qr = qr;
        this.templates = templates;
        this.defaultCap = defaultCap;
    }

    @Transactional
    public void createDefaults(Clinic clinic) {
        clinic.setMessageCap(defaultCap);
        qr.createDefaults(clinic.getId());
        template(clinic.getId(), "appointment_reminder",
                "Reminder: you have an appointment with {{1}} on {{2}} at {{3}}. Reply YES to confirm, CHANGE to reschedule, or CANCEL to cancel.");
        template(clinic.getId(), "booking_confirmation",
                "Your appointment with {{1}} is booked for {{2}} at {{3}}. Reply CHANGE to reschedule or CANCEL to cancel.");
        template(clinic.getId(), "queue_update", "Token #{{1}}: {{2}}");
        template(clinic.getId(), "followup_due",
                "It's time for your {{1}} with {{2}}. Reply to this message and we'll book a slot for you.");
        template(clinic.getId(), "appointment_update", "Update about your appointment at {{1}}: {{2}}");
    }

    private void template(Long clinicId, String name, String body) {
        MessageTemplate t = new MessageTemplate();
        t.setClinicId(clinicId);
        t.setName(name);
        t.setBody(body);
        t.setCategory(MessageTemplate.Category.UTILITY);
        templates.save(t);
    }
}
