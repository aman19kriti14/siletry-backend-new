package com.siletry.clinic;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "clinic")
@Getter
@Setter
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    /** Short locality shown in the header, e.g. "Jayanagar". */
    private String area;
    private String city;
    private String address;
    private String phone;

    /** The clinic's own WhatsApp number (coexistence), normalized digits. */
    @Column(name = "whatsapp_number", unique = true)
    private String whatsappNumber;

    @Column(name = "whatsapp_connected", nullable = false)
    private boolean whatsappConnected = false;

    /** Provider-side reference (Gupshup app id etc.). */
    @Column(name = "whatsapp_provider_ref")
    private String whatsappProviderRef;

    /** "Siletry active" toggle: when false the bot stays silent and everything goes to the inbox. */
    @Column(name = "bot_enabled", nullable = false)
    private boolean botEnabled = true;

    @Column(nullable = false)
    private String timezone = "Asia/Kolkata";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.TRIAL;

    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;

    /** Monthly cap on business-initiated (template) messages. */
    @Column(name = "message_cap", nullable = false)
    private int messageCap = 1500;

    /** Per-clinic running patient number, shown as "ID 40218". */
    @Column(name = "next_patient_no", nullable = false)
    private long nextPatientNo = 40001;

    // ----- Automations -----
    @Column(name = "reminders_enabled", nullable = false)
    private boolean remindersEnabled = true;

    @Column(name = "reminder_time", nullable = false)
    private LocalTime reminderTime = LocalTime.of(18, 0);

    @Column(name = "followups_enabled", nullable = false)
    private boolean followupsEnabled = true;

    @Column(name = "queue_updates_enabled", nullable = false)
    private boolean queueUpdatesEnabled = true;

    @Column(name = "google_review_link")
    private String googleReviewLink;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (trialEndsAt == null) trialEndsAt = createdAt.plusDays(7);
    }

    public enum Plan { TRIAL, ACTIVE, PAUSED }

    public String displayName() {
        return area == null || area.isBlank() ? name : name + ", " + area;
    }
}
