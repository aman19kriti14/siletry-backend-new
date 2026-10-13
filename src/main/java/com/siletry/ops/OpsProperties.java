package com.siletry.ops;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Settings for Siletry's own operations: alerts to the founders and the ops API.
 * Kept separate from siletry.whatsapp.* on purpose: lead alerts can be real
 * while clinic traffic stays in mock mode.
 */
@ConfigurationProperties(prefix = "siletry.ops")
public record OpsProperties(
        String apiKey,
        WhatsApp whatsapp,
        Leads leads
) {
    public OpsProperties {
        whatsapp = whatsapp == null ? new WhatsApp(null, null, null, null, null, null, null) : whatsapp;
        leads = leads == null ? new Leads(null, 0, null, null, 0, null) : leads;
    }

    public boolean apiEnabled() {
        return apiKey != null && apiKey.length() >= 24;
    }

    /** Meta Cloud API credentials of the number that sends alerts, and who receives them. */
    public record WhatsApp(
            String phoneNumberId,
            String accessToken,
            String apiVersion,
            String baseUrl,
            String notifyTo,
            String leadTemplate,
            String leadTemplateLanguage
    ) {
        public boolean configured() {
            return notBlank(phoneNumberId) && notBlank(accessToken) && !recipients().isEmpty();
        }

        /** Comma-separated in config, normalized to digits. */
        public List<String> recipients() {
            if (notBlank(notifyTo)) {
                return Arrays.stream(notifyTo.split(","))
                        .map(s -> s.replaceAll("[^0-9]", ""))
                        .filter(s -> s.length() >= 10)
                        .distinct()
                        .toList();
            }
            return List.of();
        }

        public boolean useTemplate() {
            return notBlank(leadTemplate);
        }
    }

    public record Leads(
            String ipHashSalt,
            int rateLimitMax,
            Duration rateLimitWindow,
            Duration duplicateWindow,
            int maxNotifyAttempts,
            Duration retryBaseDelay
    ) {
        public Leads {
            ipHashSalt = notBlank(ipHashSalt) ? ipHashSalt : "siletry-leads";
            rateLimitMax = rateLimitMax > 0 ? rateLimitMax : 5;
            rateLimitWindow = rateLimitWindow != null ? rateLimitWindow : Duration.ofMinutes(10);
            duplicateWindow = duplicateWindow != null ? duplicateWindow : Duration.ofHours(24);
            maxNotifyAttempts = maxNotifyAttempts > 0 ? maxNotifyAttempts : 6;
            retryBaseDelay = retryBaseDelay != null ? retryBaseDelay : Duration.ofMinutes(1);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
