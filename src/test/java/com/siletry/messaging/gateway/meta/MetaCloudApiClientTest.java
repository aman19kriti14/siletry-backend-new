package com.siletry.messaging.gateway.meta;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.siletry.messaging.gateway.WhatsAppGateway.SendResult;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MetaCloudApiClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private HttpServer server;
    private final AtomicReference<String> lastPath = new AtomicReference<>();
    private final AtomicReference<String> lastAuth = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = "{\"messages\":[{\"id\":\"wamid.TEST\"}]}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            lastPath.set(ex.getRequestURI().getPath());
            lastAuth.set(ex.getRequestHeaders().getFirst("Authorization"));
            lastBody.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] out = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, out.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(out); }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private MetaCloudApiClient client() {
        return new MetaCloudApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", "v21.0", "PNID",
                "TOKEN", Duration.ofSeconds(2), Duration.ofSeconds(2), mapper);
    }

    @Test
    void sendsTextToTheRightEndpoint() throws Exception {
        SendResult r = client().sendText("919876543210", "Hello\nthere");

        assertThat(r.ok()).isTrue();
        assertThat(r.externalId()).isEqualTo("wamid.TEST");
        assertThat(lastPath.get()).isEqualTo("/v21.0/PNID/messages");
        assertThat(lastAuth.get()).isEqualTo("Bearer TOKEN");
        JsonNode body = mapper.readTree(lastBody.get());
        assertThat(body.path("messaging_product").asText()).isEqualTo("whatsapp");
        assertThat(body.path("to").asText()).isEqualTo("919876543210");
        assertThat(body.path("type").asText()).isEqualTo("text");
        assertThat(body.path("text").path("body").asText()).isEqualTo("Hello\nthere");
    }

    @Test
    void sendsTemplateWithCleanParameters() throws Exception {
        SendResult r = client().sendTemplate("919876543210", "new_demo_lead", null, List.of("Anitha\nRao", "+91 98765 43210", "", "Clinic"));

        assertThat(r.ok()).isTrue();
        JsonNode t = mapper.readTree(lastBody.get()).path("template");
        assertThat(t.path("name").asText()).isEqualTo("new_demo_lead");
        assertThat(t.path("language").path("code").asText()).isEqualTo("en");
        JsonNode params = t.path("components").path(0).path("parameters");
        assertThat(params).hasSize(4);
        assertThat(params.path(0).path("text").asText()).isEqualTo("Anitha Rao");
        assertThat(params.path(2).path("text").asText()).isEqualTo("-");
    }

    @Test
    void reportsMetaErrorsReadably() {
        status = 400;
        response = "{\"error\":{\"message\":\"(#132001) Template name does not exist in the translation\",\"code\":132001,"
                + "\"error_data\":{\"details\":\"template name (new_demo_lead) does not exist in en\"}}}";

        SendResult r = client().sendText("919876543210", "hi");

        assertThat(r.ok()).isFalse();
        assertThat(r.error()).startsWith("HTTP 400 · code 132001 · (#132001) Template name does not exist")
                .contains("does not exist in en");
    }

    @Test
    void reportsConnectionFailureWithoutThrowing() {
        MetaCloudApiClient dead = new MetaCloudApiClient("http://127.0.0.1:1", null, "PNID", "TOKEN",
                Duration.ofMillis(500), Duration.ofMillis(500), mapper);
        SendResult r = dead.sendText("919876543210", "hi");
        assertThat(r.ok()).isFalse();
        assertThat(r.error()).startsWith("Could not reach Meta");
    }
}
