package com.siletry.messaging.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Placeholder for the real adapter once the Gupshup partner account is approved.
 * Implement these four methods against Gupshup's partner API and set WHATSAPP_PROVIDER=gupshup.
 * Nothing else in the codebase needs to change.
 */
@Component
@ConditionalOnProperty(name = "siletry.whatsapp.provider", havingValue = "gupshup")
public class GupshupWhatsAppGateway implements WhatsAppGateway {

    @Override
    public String name() {
        return "gupshup";
    }

    @Override
    public SendResult sendText(String fromNumber, String to, String body) {
        return SendResult.failed("Gupshup adapter not implemented yet");
    }

    @Override
    public SendResult sendButtons(String fromNumber, String to, String body, List<String> buttons) {
        return SendResult.failed("Gupshup adapter not implemented yet");
    }

    @Override
    public SendResult sendTemplate(String fromNumber, String to, String templateName, String language, List<String> params) {
        return SendResult.failed("Gupshup adapter not implemented yet");
    }

    @Override
    public WebhookBatch parseWebhook(String rawBody) {
        throw new UnsupportedOperationException("Gupshup webhook parsing not implemented yet");
    }
}
