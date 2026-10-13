package com.siletry.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.siletry.lead.Lead;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class OpsNotifierTest {

    private static Lead lead() {
        Lead l = new Lead();
        l.setId(1L);
        l.setName("Dr Rajesh Ranjan");
        l.setPhone("918228968530");
        l.setCity("Patna");
        l.setCreatedAt(LocalDateTime.of(2026, 10, 13, 23, 7));
        return l;
    }

    @Test
    void textMatchesTheTeamAlertFormat() {
        assertThat(OpsNotifier.text(lead())).isEqualTo("""
                💬 New Demo Request

                Name: Dr Rajesh Ranjan
                WhatsApp: +91 82289 68530
                City: Patna
                Time: 13 Oct 2026, 11:07 PM

                Contact them within a few hours to book their demo.
                https://wa.me/918228968530""");
    }

    @Test
    void templateParamsFillMissingClinic() {
        assertThat(OpsNotifier.templateParams(lead())).containsExactly("Dr Rajesh Ranjan", "+91 82289 68530", "Patna", "not given");
    }

    @Test
    void notConfiguredIsReportedNotThrown() {
        OpsNotifier n = new OpsNotifier(new OpsProperties(null, null, null), new ObjectMapper());
        assertThat(n.enabled()).isFalse();
        assertThat(n.newLead(lead()).outcome()).isEqualTo(OpsNotifier.Outcome.NOT_CONFIGURED);
    }

    @Test
    void recipientsAreNormalizedAndDeduped() {
        var wa = new OpsProperties.WhatsApp("id", "tok", null, null, "+91 82289 68530, 918228968530 ,919000000001,12", null, null);
        assertThat(wa.recipients()).containsExactly("918228968530", "919000000001");
        assertThat(wa.configured()).isTrue();
        assertThat(wa.useTemplate()).isFalse();
    }
}
