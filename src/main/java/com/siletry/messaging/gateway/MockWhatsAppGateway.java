package com.siletry.messaging.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siletry.messaging.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Pretends to send. Every outbound message is still stored in the database, so the
 * web app and the dev panel show exactly what a patient would receive.
 *
 * Webhook body format for testing:
 *   {"type":"message","to":"918047182200","from":"919845012345","name":"Lakshmi","text":"hi","id":"abc"}
 *   {"type":"status","id":"mock-...","status":"READ"}
 */
@Component
@ConditionalOnProperty(name = "siletry.whatsapp.provider", havingValue = "mock", matchIfMissing = true)
public class MockWhatsAppGateway implements WhatsAppGateway {

    private static final Logger log = LoggerFactory.getLogger(MockWhatsAppGateway.class);
    private final ObjectMapper mapper;

    public MockWhatsAppGateway(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public SendResult sendText(String fromNumber, String to, String body) {
        log.info("[WA mock] {} -> {}: {}", fromNumber, to, body);
        return SendResult.ok("mock-" + UUID.randomUUID());
    }

    @Override
    public SendResult sendButtons(String fromNumber, String to, String body, List<String> buttons) {
        log.info("[WA mock] {} -> {}: {} {}", fromNumber, to, body, buttons);
        return SendResult.ok("mock-" + UUID.randomUUID());
    }

    @Override
    public SendResult sendTemplate(String fromNumber, String to, String templateName, String language, List<String> params) {
        log.info("[WA mock] {} -> {} TEMPLATE {} ({}) {}", fromNumber, to, templateName, language, params);
        return SendResult.ok("mock-" + UUID.randomUUID());
    }

    @Override
    public WebhookBatch parseWebhook(String rawBody) {
        List<InboundMessage> messages = new ArrayList<>();
        List<StatusUpdate> statuses = new ArrayList<>();
        try {
            JsonNode root = mapper.readTree(rawBody);
            List<JsonNode> items = new ArrayList<>();
            if (root.isArray()) root.forEach(items::add); else items.add(root);
            for (JsonNode n : items) {
                String type = n.path("type").asText("message");
                if ("status".equals(type)) {
                    statuses.add(new StatusUpdate(n.path("id").asText(),
                            Message.Status.valueOf(n.path("status").asText("DELIVERED").toUpperCase()),
                            n.path("error").asText(null)));
                } else {
                    messages.add(new InboundMessage(
                            n.path("to").asText(null),
                            n.path("from").asText(),
                            n.path("name").asText(null),
                            n.path("text").asText(""),
                            n.path("id").asText("mock-in-" + UUID.randomUUID())));
                }
            }
        } catch (Exception e) {
            log.warn("Could not parse mock webhook: {}", e.getMessage());
        }
        return new WebhookBatch(messages, statuses);
    }
}
