package com.siletry.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siletry.common.Phone;
import com.siletry.lead.Lead;
import com.siletry.messaging.gateway.WhatsAppGateway.SendResult;
import com.siletry.messaging.gateway.meta.MetaCloudApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Sends operational alerts (new demo leads) to the Siletry team on WhatsApp through
 * Meta's Cloud API. Independent of siletry.whatsapp.provider, so clinic traffic can stay
 * in mock mode while alerts are real.
 */
@Component
public class OpsNotifier {

    private static final Logger log = LoggerFactory.getLogger(OpsNotifier.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH);

    public enum Outcome { SENT, FAILED, NOT_CONFIGURED }

    public record Result(Outcome outcome, String messageId, String error) {}

    private final OpsProperties.WhatsApp config;
    private final MetaCloudApiClient client; // null when not configured

    public OpsNotifier(OpsProperties props, ObjectMapper mapper) {
        this.config = props.whatsapp();
        this.client = config.configured()
                ? new MetaCloudApiClient(config.baseUrl(), config.apiVersion(), config.phoneNumberId(), config.accessToken(),
                        Duration.ofSeconds(5), Duration.ofSeconds(10), mapper)
                : null;
        if (client == null) {
            log.warn("Lead alerts are OFF: set OPS_WA_PHONE_NUMBER_ID, OPS_WA_ACCESS_TOKEN and OPS_WA_NOTIFY_TO");
        } else {
            log.info("Lead alerts ON via Meta Cloud API ({} recipient(s), {} mode)",
                    config.recipients().size(), config.useTemplate() ? "template" : "text");
        }
    }

    public boolean enabled() {
        return client != null;
    }

    /** Succeeds if at least one recipient got the alert. */
    public Result newLead(Lead lead) {
        if (client == null) return new Result(Outcome.NOT_CONFIGURED, null, "Lead alerts are not configured");
        List<String> errors = new ArrayList<>();
        String firstId = null;
        for (String to : config.recipients()) {
            SendResult r = config.useTemplate()
                    ? client.sendTemplate(to, config.leadTemplate(), config.leadTemplateLanguage(), templateParams(lead))
                    : client.sendText(to, text(lead));
            if (r.ok()) {
                if (firstId == null) firstId = r.externalId();
            } else {
                errors.add(mask(to) + ": " + r.error());
            }
        }
        if (firstId != null) {
            if (!errors.isEmpty()) log.warn("Lead {} alert partly failed: {}", lead.getId(), errors);
            return new Result(Outcome.SENT, firstId, errors.isEmpty() ? null : String.join("; ", errors));
        }
        return new Result(Outcome.FAILED, null, String.join("; ", errors));
    }

    static String text(Lead lead) {
        StringBuilder sb = new StringBuilder();
        sb.append("💬 New Demo Request\n\n");
        sb.append("Name: ").append(lead.getName()).append('\n');
        sb.append("WhatsApp: ").append(Phone.pretty(lead.getPhone())).append('\n');
        sb.append("City: ").append(lead.getCity()).append('\n');
        if (lead.getClinicName() != null) sb.append("Clinic: ").append(lead.getClinicName()).append('\n');
        sb.append("Time: ").append(lead.getCreatedAt().format(WHEN)).append('\n');
        if (lead.getSubmissionCount() > 1) sb.append("Note: submitted ").append(lead.getSubmissionCount()).append(" times\n");
        sb.append("\nContact them within a few hours to book their demo.\n");
        sb.append("https://wa.me/").append(lead.getPhone());
        return sb.toString();
    }

    /** Matches the template body: Name {{1}}, WhatsApp {{2}}, City {{3}}, Clinic {{4}}. */
    static List<String> templateParams(Lead lead) {
        return List.of(
                lead.getName(),
                Phone.pretty(lead.getPhone()),
                lead.getCity(),
                lead.getClinicName() == null ? "not given" : lead.getClinicName());
    }

    private static String mask(String number) {
        return number.length() <= 4 ? "****" : "****" + number.substring(number.length() - 4);
    }
}
