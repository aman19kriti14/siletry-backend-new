package com.siletry.messaging;

import com.siletry.messaging.gateway.WhatsAppGateway;
import com.siletry.messaging.gateway.WhatsAppGateway.WebhookBatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Provider webhooks. Processed synchronously for v1 (fine at this volume);
 * move to a queue (SQS or a jobs table) when traffic grows.
 */
@RestController
@RequestMapping("/api/webhooks/whatsapp")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WhatsAppGateway gateway;
    private final InboundMessageService inbound;
    private final AdminCommandService admin;
    private final String secret;

    public WebhookController(WhatsAppGateway gateway, InboundMessageService inbound, AdminCommandService admin,
                             @Value("${siletry.whatsapp.webhook-secret}") String secret) {
        this.gateway = gateway;
        this.inbound = inbound;
        this.admin = admin;
        this.secret = secret;
    }

    /** Patient messages to clinic numbers + delivery statuses. */
    @PostMapping
    public ResponseEntity<Map<String, Object>> patient(@RequestHeader(value = "X-Siletry-Webhook-Secret", required = false) String header,
                                                       @RequestBody String body) {
        if (!secret.equals(header)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        WebhookBatch batch = gateway.parseWebhook(body);
        int handled = 0;
        for (var m : batch.messages()) {
            try {
                if (inbound.handle(m) != null) handled++;
            } catch (Exception e) {
                log.error("Failed to process inbound message {}", m.externalId(), e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build(); // provider retries; dedupe protects us
            }
        }
        batch.statuses().forEach(inbound::applyStatus);
        return ResponseEntity.ok(Map.of("messages", handled, "statuses", batch.statuses().size()));
    }

    /** Doctors / receptionists messaging the central Siletry number. */
    @PostMapping("/admin")
    public ResponseEntity<Map<String, Object>> adminNumber(@RequestHeader(value = "X-Siletry-Webhook-Secret", required = false) String header,
                                                           @RequestBody String body) {
        if (!secret.equals(header)) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        WebhookBatch batch = gateway.parseWebhook(body);
        batch.messages().forEach(m -> admin.handle(m.fromPhone(), m.text()));
        return ResponseEntity.ok(Map.of("messages", batch.messages().size()));
    }
}
