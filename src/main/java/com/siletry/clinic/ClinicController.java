package com.siletry.clinic;

import com.siletry.auth.CurrentUser;
import com.siletry.common.ApiException;
import com.siletry.common.Phone;
import com.siletry.messaging.MessageUsage;
import com.siletry.messaging.OutboundMessageService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/** Settings, automations, WhatsApp connection and message usage. */
@RestController
@RequestMapping("/api/clinic")
public class ClinicController {

    public record ClinicView(Long id, String name, String area, String city, String address, String phone,
                             String whatsappNumber, boolean whatsappConnected, boolean botEnabled,
                             String plan, LocalDateTime trialEndsAt, int messageCap,
                             boolean remindersEnabled, LocalTime reminderTime, boolean followupsEnabled,
                             boolean queueUpdatesEnabled, String googleReviewLink) {
        static ClinicView of(Clinic c) {
            return new ClinicView(c.getId(), c.getName(), c.getArea(), c.getCity(), c.getAddress(),
                    Phone.pretty(c.getPhone()), Phone.pretty(c.getWhatsappNumber()), c.isWhatsappConnected(),
                    c.isBotEnabled(), c.getPlan().name(), c.getTrialEndsAt(), c.getMessageCap(),
                    c.isRemindersEnabled(), c.getReminderTime(), c.isFollowupsEnabled(), c.isQueueUpdatesEnabled(),
                    c.getGoogleReviewLink());
        }
    }

    public record UpdateClinic(String name, String area, String city, String address, String phone,
                               Boolean botEnabled, Boolean remindersEnabled, LocalTime reminderTime,
                               Boolean followupsEnabled, Boolean queueUpdatesEnabled, String googleReviewLink) {}

    public record ConnectWhatsApp(@NotBlank String number) {}

    public record UsageView(String month, int businessInitiated, int cap, int serviceReplies, int inbound,
                            int percentUsed) {}

    private final ClinicRepository clinics;
    private final OutboundMessageService outbound;

    public ClinicController(ClinicRepository clinics, OutboundMessageService outbound) {
        this.clinics = clinics;
        this.outbound = outbound;
    }

    private Clinic mine() {
        return clinics.findById(CurrentUser.clinicId()).orElseThrow(() -> ApiException.notFound("Clinic"));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ClinicView get() {
        return ClinicView.of(mine());
    }

    @PatchMapping
    @Transactional
    public ClinicView update(@RequestBody UpdateClinic req) {
        CurrentUser.requireOwner();
        Clinic c = mine();
        if (req.name() != null) c.setName(req.name().trim());
        if (req.area() != null) c.setArea(req.area());
        if (req.city() != null) c.setCity(req.city());
        if (req.address() != null) c.setAddress(req.address());
        if (req.phone() != null) c.setPhone(Phone.normalize(req.phone()));
        if (req.botEnabled() != null) c.setBotEnabled(req.botEnabled());
        if (req.remindersEnabled() != null) c.setRemindersEnabled(req.remindersEnabled());
        if (req.reminderTime() != null) c.setReminderTime(req.reminderTime());
        if (req.followupsEnabled() != null) c.setFollowupsEnabled(req.followupsEnabled());
        if (req.queueUpdatesEnabled() != null) c.setQueueUpdatesEnabled(req.queueUpdatesEnabled());
        if (req.googleReviewLink() != null) c.setGoogleReviewLink(req.googleReviewLink());
        return ClinicView.of(c);
    }

    /**
     * Mock connection for now. With Gupshup this becomes the embedded-signup callback
     * that stores the provider app id and the verified number.
     */
    @PostMapping("/whatsapp/connect")
    @Transactional
    public ClinicView connect(@Valid @RequestBody ConnectWhatsApp req) {
        CurrentUser.requireOwner();
        Clinic c = mine();
        String number = Phone.normalize(req.number());
        clinics.findByWhatsappNumber(number).filter(o -> !o.getId().equals(c.getId()))
                .ifPresent(o -> { throw ApiException.conflict("This number is linked to another clinic"); });
        c.setWhatsappNumber(number);
        c.setWhatsappConnected(true);
        return ClinicView.of(c);
    }

    @PostMapping("/whatsapp/disconnect")
    @Transactional
    public ClinicView disconnect() {
        CurrentUser.requireOwner();
        Clinic c = mine();
        c.setWhatsappConnected(false);
        return ClinicView.of(c);
    }

    @GetMapping("/usage")
    @Transactional
    public UsageView usage() {
        Clinic c = mine();
        MessageUsage u = outbound.monthUsage(c.getId());
        int pct = c.getMessageCap() == 0 ? 100 : (int) Math.round(100.0 * u.getBusinessInitiated() / c.getMessageCap());
        return new UsageView(u.getMonth(), u.getBusinessInitiated(), c.getMessageCap(), u.getServiceReplies(), u.getInbound(), pct);
    }
}
