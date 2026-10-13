package com.siletry.messaging.gateway.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.siletry.messaging.gateway.WhatsAppGateway.SendResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;

/**
 * Thin, dependency-free client for Meta's WhatsApp Cloud API (graph.facebook.com).
 * One instance per sending number. Thread-safe.
 *
 * Only the HTTP layer lives here: no business rules, no persistence. Callers decide
 * whether a free-form text is allowed (24-hour window) or a template is required.
 */
public class MetaCloudApiClient {

    private static final Logger log = LoggerFactory.getLogger(MetaCloudApiClient.class);
    public static final String DEFAULT_BASE_URL = "https://graph.facebook.com";
    public static final String DEFAULT_API_VERSION = "v21.0";

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String messagesUrl;
    private final String accessToken;
    private final Duration requestTimeout;

    public MetaCloudApiClient(String baseUrl, String apiVersion, String phoneNumberId, String accessToken,
                              Duration connectTimeout, Duration requestTimeout, ObjectMapper mapper) {
        if (isBlank(phoneNumberId)) throw new IllegalArgumentException("phoneNumberId is required");
        if (isBlank(accessToken)) throw new IllegalArgumentException("accessToken is required");
        String base = isBlank(baseUrl) ? DEFAULT_BASE_URL : baseUrl.replaceAll("/+$", "");
        String version = isBlank(apiVersion) ? DEFAULT_API_VERSION : apiVersion;
        this.messagesUrl = base + "/" + version + "/" + phoneNumberId.trim() + "/messages";
        this.accessToken = accessToken.trim();
        this.requestTimeout = Objects.requireNonNullElse(requestTimeout, Duration.ofSeconds(10));
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Objects.requireNonNullElse(connectTimeout, Duration.ofSeconds(5)))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    /** Free-form text. Meta only delivers it inside the 24-hour customer service window. */
    public SendResult sendText(String to, String body) {
        ObjectNode payload = base(to);
        payload.put("type", "text");
        ObjectNode text = payload.putObject("text");
        text.put("preview_url", false);
        text.put("body", body);
        return post(payload);
    }

    /** Approved template with positional body parameters ({{1}}, {{2}}, ...). Works any time. */
    public SendResult sendTemplate(String to, String templateName, String languageCode, List<String> bodyParams) {
        ObjectNode payload = base(to);
        payload.put("type", "template");
        ObjectNode template = payload.putObject("template");
        template.put("name", templateName);
        template.putObject("language").put("code", isBlank(languageCode) ? "en" : languageCode);
        if (bodyParams != null && !bodyParams.isEmpty()) {
            ObjectNode component = template.putArray("components").addObject();
            component.put("type", "body");
            ArrayNode params = component.putArray("parameters");
            for (String p : bodyParams) {
                // Meta rejects empty parameters and newlines/tabs inside them
                String safe = p == null || p.isBlank() ? "-" : p.replaceAll("[\\r\\n\\t]+", " ").trim();
                params.addObject().put("type", "text").put("text", safe);
            }
        }
        return post(payload);
    }

    private ObjectNode base(String to) {
        ObjectNode payload = mapper.createObjectNode();
        payload.put("messaging_product", "whatsapp");
        payload.put("recipient_type", "individual");
        payload.put("to", to);
        return payload;
    }

    private SendResult post(ObjectNode payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(messagesUrl))
                    .timeout(requestTimeout)
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = parse(response.body());
            if (response.statusCode() / 100 == 2) {
                String id = json.path("messages").path(0).path("id").asText(null);
                return id != null ? SendResult.ok(id) : SendResult.failed("Meta accepted the request but returned no message id");
            }
            return SendResult.failed(describeError(response.statusCode(), json));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.failed("Interrupted while calling Meta");
        } catch (Exception e) {
            log.warn("Meta Cloud API call failed: {}", e.toString());
            return SendResult.failed("Could not reach Meta: " + e.getClass().getSimpleName());
        }
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? mapper.createObjectNode() : mapper.readTree(body);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    /** "HTTP 400 · code 132001 · Template name does not exist in the translation" */
    static String describeError(int status, JsonNode json) {
        JsonNode err = json.path("error");
        StringBuilder sb = new StringBuilder("HTTP ").append(status);
        if (err.has("code")) sb.append(" · code ").append(err.path("code").asText());
        String detail = err.path("error_data").path("details").asText(null);
        String message = err.path("message").asText(null);
        if (message != null) sb.append(" · ").append(message);
        if (detail != null && !detail.equals(message)) sb.append(" · ").append(detail);
        String out = sb.toString();
        return out.length() > 480 ? out.substring(0, 480) : out;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
