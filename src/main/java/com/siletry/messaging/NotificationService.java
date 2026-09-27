package com.siletry.messaging;

import com.siletry.appointment.Appointment;
import com.siletry.clinic.Clinic;
import com.siletry.clinic.ClinicRepository;
import com.siletry.doctor.Doctor;
import com.siletry.doctor.DoctorRepository;
import com.siletry.messaging.OutboundMessageService.Outgoing;
import com.siletry.patient.Patient;
import com.siletry.patient.PatientRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Patient-facing notifications triggered by appointment and queue events. */
@Service
public class NotificationService {

    private final OutboundMessageService outbound;
    private final ClinicRepository clinics;
    private final PatientRepository patients;
    private final DoctorRepository doctors;

    public NotificationService(OutboundMessageService outbound, ClinicRepository clinics,
                               PatientRepository patients, DoctorRepository doctors) {
        this.outbound = outbound;
        this.clinics = clinics;
        this.patients = patients;
        this.doctors = doctors;
    }

    private record Ctx(Clinic clinic, Patient patient, Doctor doctor) {}

    private Ctx ctx(Appointment a) {
        return new Ctx(
                clinics.findById(a.getClinicId()).orElseThrow(),
                patients.findById(a.getPatientId()).orElseThrow(),
                doctors.findById(a.getDoctorId()).orElseThrow());
    }

    @Transactional
    public void bookingConfirmed(Appointment a) {
        Ctx c = ctx(a);
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.booked(c.clinic(), c.doctor(), a.getStartAt()), List.of(), "booking_confirmation"));
    }

    @Transactional
    public void rescheduled(Appointment a) {
        Ctx c = ctx(a);
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.rescheduled(c.clinic(), c.doctor(), a.getStartAt()), List.of(), "booking_confirmation"));
    }

    @Transactional
    public void cancelledByClinic(Appointment a, String reason) {
        Ctx c = ctx(a);
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.clinicCancelled(c.doctor(), a.getStartAt(), reason), List.of(), "appointment_update"));
    }

    @Transactional
    public void reminder(Appointment a) {
        Ctx c = ctx(a);
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.reminder(c.clinic(), c.doctor(), a.getStartAt()),
                List.of(MessageTexts.BTN_YES, MessageTexts.BTN_CHANGE, MessageTexts.BTN_CANCEL),
                "appointment_reminder"));
    }

    @Transactional
    public void checkedIn(Appointment a, int ahead, int eta) {
        Ctx c = ctx(a);
        if (!c.clinic().isQueueUpdatesEnabled()) return;
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.checkedIn(a.getTokenNumber(), ahead, eta), List.of(), "queue_update"));
    }

    @Transactional
    public void comeIn(Appointment a) {
        Ctx c = ctx(a);
        if (!c.clinic().isQueueUpdatesEnabled()) return;
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.comeIn(a.getTokenNumber(), c.doctor()), List.of(), "queue_update"));
    }

    @Transactional
    public void almostTurn(Appointment a) {
        Ctx c = ctx(a);
        if (!c.clinic().isQueueUpdatesEnabled()) return;
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.almostTurn(a.getTokenNumber()), List.of(), "queue_update"));
    }

    @Transactional
    public void delay(Appointment a, int minutes, String note) {
        Ctx c = ctx(a);
        outbound.send(c.clinic(), c.patient(), Outgoing.template(
                MessageTexts.delay(c.doctor(), minutes, note), List.of(), "appointment_update"));
    }
}
