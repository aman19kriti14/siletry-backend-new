package com.siletry.lead;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full flow against a real Postgres (CI service, or docker-compose locally) and a fake Meta API:
 * website POST -> lead saved -> WhatsApp alert sent after commit -> visible in the ops API.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LeadFlowIntegrationTest {

    static final List<String> metaRequests = new CopyOnWriteArrayList<>();
    static final HttpServer fakeMeta;
    static final String OPS_KEY = "test-ops-key-0123456789abcdef";

    static {
        try {
            fakeMeta = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            fakeMeta.createContext("/", ex -> {
                metaRequests.add(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] out = "{\"messages\":[{\"id\":\"wamid.IT\"}]}".getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(200, out.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(out); }
            });
            fakeMeta.start();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("siletry.seed-demo", () -> "false");
        r.add("siletry.ops.api-key", () -> OPS_KEY);
        r.add("siletry.ops.whatsapp.base-url", () -> "http://127.0.0.1:" + fakeMeta.getAddress().getPort());
        r.add("siletry.ops.whatsapp.phone-number-id", () -> "PNID");
        r.add("siletry.ops.whatsapp.access-token", () -> "TOKEN");
        r.add("siletry.ops.whatsapp.notify-to", () -> "919000000001");
        r.add("siletry.ops.leads.rate-limit-max", () -> "100");
    }

    @AfterAll
    static void stopMeta() {
        fakeMeta.stop(0);
    }

    @Autowired MockMvc mvc;
    @Autowired LeadRepository leads;
    @Autowired ObjectMapper mapper;

    @BeforeEach
    void clean() {
        leads.deleteAll();
        metaRequests.clear();
    }

    @Test
    void websiteLeadIsSavedAlertedAndListed() throws Exception {
        mvc.perform(post("/api/public/leads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Origin", "http://localhost:5173")
                        .header("X-Forwarded-For", "203.0.113.9")
                        .content("""
                                {"name":"Dr. Anitha Rao","phone":"98765 43210","city":"Indore","clinic":"Sunrise Clinic",
                                 "website":"","page":"/","utmSource":"google"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.ok").value(true));

        Lead saved = waitForAlert();
        assertThat(saved.getPhone()).isEqualTo("919876543210");
        assertThat(saved.getNotifyStatus()).isEqualTo(Lead.NotifyStatus.SENT);
        assertThat(saved.getNotifyMessageId()).isEqualTo("wamid.IT");
        assertThat(saved.getNotifyAttempts()).isEqualTo(1);

        assertThat(metaRequests).hasSize(1);
        JsonNode sent = mapper.readTree(metaRequests.get(0));
        assertThat(sent.path("to").asText()).isEqualTo("919000000001");
        assertThat(sent.path("type").asText()).isEqualTo("text");
        assertThat(sent.path("text").path("body").asText())
                .startsWith("💬 New Demo Request")
                .contains("Name: Dr. Anitha Rao", "WhatsApp: +91 98765 43210", "City: Indore", "Clinic: Sunrise Clinic",
                        "https://wa.me/919876543210");

        // Same number again: no second row, no second alert
        mvc.perform(post("/api/public/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Anitha\",\"phone\":\"+919876543210\",\"city\":\"Indore\"}"))
                .andExpect(status().isAccepted());
        Thread.sleep(500);
        assertThat(leads.count()).isEqualTo(1);
        assertThat(leads.findAll().get(0).getSubmissionCount()).isEqualTo(2);
        assertThat(metaRequests).hasSize(1);

        // Ops API
        mvc.perform(get("/api/ops/leads")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/ops/leads").header("X-Ops-Key", OPS_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Dr. Anitha Rao"))
                .andExpect(jsonPath("$.content[0].notifyStatus").value("SENT"));
        mvc.perform(patch("/api/ops/leads/" + saved.getId()).header("X-Ops-Key", OPS_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"CONTACTED\",\"note\":\"Demo Friday\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONTACTED"));
    }

    @Test
    void invalidInputGetsAFriendlyError() throws Exception {
        mvc.perform(post("/api/public/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ravi\",\"phone\":\"123\",\"city\":\"Pune\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Please enter a valid 10-digit mobile number."));
        assertThat(leads.count()).isZero();
    }

    @Test
    void publicEndpointNeedsNoLogin() throws Exception {
        mvc.perform(post("/api/public/leads").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Bot\",\"phone\":\"9876543210\",\"city\":\"X\",\"website\":\"spam\"}"))
                .andExpect(status().isAccepted());
        assertThat(leads.count()).isZero();
    }

    private Lead waitForAlert() throws InterruptedException {
        for (int i = 0; i < 50; i++) {
            List<Lead> all = leads.findAll();
            if (!all.isEmpty() && all.get(0).getNotifyStatus() == Lead.NotifyStatus.SENT) return all.get(0);
            Thread.sleep(100);
        }
        List<Lead> all = leads.findAll();
        assertThat(all).hasSize(1);
        return all.get(0);
    }
}
