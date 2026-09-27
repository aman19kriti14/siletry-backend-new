package com.siletry.messaging.gateway;

import com.siletry.messaging.Message;

import java.util.List;

/**
 * Everything provider-specific lives behind this interface.
 * Business logic only ever sees InboundMessage / StatusUpdate, never a Gupshup or Meta payload.
 */
public interface WhatsAppGateway {

    String name();

    /** Free-form text. Only allowed inside the 24-hour window. */
    SendResult sendText(String fromNumber, String to, String body);

    /** Up to 3 reply buttons; more than 3 should be sent as a list message by the adapter. Window only. */
    SendResult sendButtons(String fromNumber, String to, String body, List<String> buttons);

    /** Approved template. Works outside the 24-hour window; costs money. */
    SendResult sendTemplate(String fromNumber, String to, String templateName, String language, List<String> params);

    /** Turns the provider's webhook body into our own events. */
    WebhookBatch parseWebhook(String rawBody);

    record SendResult(boolean ok, String externalId, String error) {
        public static SendResult ok(String id) { return new SendResult(true, id, null); }
        public static SendResult failed(String error) { return new SendResult(false, null, error); }
    }

    record InboundMessage(String toNumber, String fromPhone, String profileName, String text, String externalId) {}

    record StatusUpdate(String externalId, Message.Status status, String error) {}

    record WebhookBatch(List<InboundMessage> messages, List<StatusUpdate> statuses) {}
}
