package com.siletry.messaging;

import com.siletry.clinic.Clinic;
import com.siletry.messaging.gateway.WhatsAppGateway;
import com.siletry.messaging.gateway.WhatsAppGateway.SendResult;
import com.siletry.patient.Patient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;

/**
 * The one place that sends WhatsApp messages to patients.
 * Decides free reply vs paid template (24-hour rule), enforces the monthly cap and opt-out,
 * stores every message, and updates the conversation.
 */
@Service
public class OutboundMessageService {

    private static final Logger log = LoggerFactory.getLogger(OutboundMessageService.class);
    public static final String GENERIC_TEMPLATE = "siletry_update";

    private final WhatsAppGateway gateway;
    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final MessageUsageRepository usage;
    private final String adminNumber;

    public OutboundMessageService(WhatsAppGateway gateway, MessageRepository messages,
                                  ConversationRepository conversations, MessageUsageRepository usage,
                                  @Value("${siletry.whatsapp.admin-number}") String adminNumber) {
        this.gateway = gateway;
        this.messages = messages;
        this.conversations = conversations;
        this.usage = usage;
        this.adminNumber = adminNumber;
    }

    public record Outgoing(String body, List<String> buttons, Message.Sender sender, Long userId, String templateName) {
        public static Outgoing bot(String body) { return new Outgoing(body, List.of(), Message.Sender.SILETRY, null, null); }
        public static Outgoing bot(String body, List<String> buttons) { return new Outgoing(body, buttons, Message.Sender.SILETRY, null, null); }
        public static Outgoing template(String body, List<String> buttons, String template) {
            return new Outgoing(body, buttons, Message.Sender.SILETRY, null, template);
        }
        public static Outgoing staff(String body, Long userId) { return new Outgoing(body, List.of(), Message.Sender.STAFF, userId, null); }
    }

    @Transactional
    public Conversation conversationFor(Long clinicId, Long patientId) {
        return conversations.findByClinicIdAndPatientId(clinicId, patientId).orElseGet(() -> {
            Conversation c = new Conversation();
            c.setClinicId(clinicId);
            c.setPatientId(patientId);
            c.setState(ConversationState.HANDLED);
            return conversations.save(c);
        });
    }

    @Transactional
    public Message send(Clinic clinic, Patient patient, Outgoing out) {
        Conversation conv = conversationFor(clinic.getId(), patient.getId());
        boolean window = conv.isWindowOpen();

        Message m = new Message();
        m.setClinicId(clinic.getId());
        m.setConversationId(conv.getId());
        m.setPatientId(patient.getId());
        m.setDirection(Message.Direction.OUT);
        m.setSender(out.sender());
        m.setBody(out.body());
        m.setSentByUserId(out.userId());
        m.setLanguage(patient.getPreferredLanguage());
        List<String> buttons = out.buttons() == null ? List.of() : out.buttons().stream().limit(10).toList();
        if (!buttons.isEmpty()) m.setButtons(String.join("\n", buttons));

        SendResult result;
        if (window) {
            m.setKind(buttons.isEmpty() ? Message.Kind.TEXT : Message.Kind.BUTTONS);
            result = buttons.isEmpty()
                    ? gateway.sendText(clinic.getWhatsappNumber(), patient.getPhone(), out.body())
                    : gateway.sendButtons(clinic.getWhatsappNumber(), patient.getPhone(), out.body(), buttons);
            bump(clinic.getId(), false);
        } else {
            // Outside the 24-hour window: must be an approved template, counts toward the cap
            m.setKind(Message.Kind.TEMPLATE);
            m.setBusinessInitiated(true);
            String template = out.templateName() == null ? GENERIC_TEMPLATE : out.templateName();
            m.setTemplateName(template);
            if (patient.isOptedOut()) {
                result = SendResult.failed("Patient opted out (sent STOP)");
            } else if (out.sender() == Message.Sender.STAFF && out.templateName() == null) {
                result = SendResult.failed("More than 24 hours since the patient's last message. Only approved templates can be sent now.");
            } else if (monthUsage(clinic.getId()).getBusinessInitiated() >= clinic.getMessageCap()) {
                result = SendResult.failed("Monthly message cap reached (" + clinic.getMessageCap() + ")");
            } else {
                result = gateway.sendTemplate(clinic.getWhatsappNumber(), patient.getPhone(), template,
                        patient.getPreferredLanguage() == null ? "en" : patient.getPreferredLanguage(), List.of(out.body()));
                if (result.ok()) bump(clinic.getId(), true);
            }
        }

        m.setExternalId(result.externalId());
        m.setStatus(result.ok() ? Message.Status.SENT : Message.Status.FAILED);
        m.setError(result.error());
        m.setStatusAt(LocalDateTime.now());
        messages.save(m);
        if (!result.ok()) log.warn("WhatsApp send to patient {} failed: {}", patient.getId(), result.error());

        conv.setLastMessageAt(LocalDateTime.now());
        conv.setLastMessagePreview(preview(out.body()));
        return m;
    }

    /** Messages from the central Siletry number to doctors / receptionists. */
    public void sendAdmin(String toPhone, String body, List<String> buttons) {
        if (toPhone == null || toPhone.isBlank()) return;
        SendResult r = buttons == null || buttons.isEmpty()
                ? gateway.sendText(adminNumber, toPhone, body)
                : gateway.sendButtons(adminNumber, toPhone, body, buttons);
        if (!r.ok()) log.warn("Admin message to {} failed: {}", toPhone, r.error());
    }

    @Transactional
    public MessageUsage monthUsage(Long clinicId) {
        String month = YearMonth.now().toString();
        return usage.findByClinicIdAndMonth(clinicId, month).orElseGet(() -> {
            MessageUsage u = new MessageUsage();
            u.setClinicId(clinicId);
            u.setMonth(month);
            return usage.save(u);
        });
    }

    @Transactional
    public void countInbound(Long clinicId) {
        MessageUsage u = monthUsage(clinicId);
        u.setInbound(u.getInbound() + 1);
    }

    private void bump(Long clinicId, boolean businessInitiated) {
        MessageUsage u = monthUsage(clinicId);
        if (businessInitiated) u.setBusinessInitiated(u.getBusinessInitiated() + 1);
        else u.setServiceReplies(u.getServiceReplies() + 1);
    }

    public static String preview(String body) {
        if (body == null) return null;
        String oneLine = body.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 140 ? oneLine.substring(0, 137) + "..." : oneLine;
    }
}
