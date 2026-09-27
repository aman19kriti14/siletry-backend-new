package com.siletry.auth;

import com.siletry.auth.AuthDtos.*;
import com.siletry.clinic.Clinic;
import com.siletry.clinic.ClinicRepository;
import com.siletry.clinic.ClinicSetupService;
import com.siletry.common.ApiException;
import com.siletry.common.Phone;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuthService {

    private final AppUserRepository users;
    private final ClinicRepository clinics;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final ClinicSetupService clinicSetup;

    public AuthService(AppUserRepository users, ClinicRepository clinics, PasswordEncoder encoder,
                       JwtService jwt, ClinicSetupService clinicSetup) {
        this.users = users;
        this.clinics = clinics;
        this.encoder = encoder;
        this.jwt = jwt;
        this.clinicSetup = clinicSetup;
    }

    @Transactional
    public AuthResponse signup(SignupRequest req) {
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        String wa = Phone.normalize(req.whatsappNumber());
        if (wa != null && clinics.findByWhatsappNumber(wa).isPresent()) {
            throw ApiException.conflict("This WhatsApp number is already linked to another clinic");
        }
        Clinic clinic = new Clinic();
        clinic.setName(req.clinicName().trim());
        clinic.setArea(req.area());
        clinic.setCity(req.city());
        clinic.setPhone(Phone.normalize(req.phone()));
        clinic.setWhatsappNumber(wa);
        clinic = clinics.save(clinic);

        AppUser owner = new AppUser();
        owner.setClinicId(clinic.getId());
        owner.setName(req.ownerName().trim());
        owner.setEmail(req.email().trim().toLowerCase());
        owner.setPhone(Phone.normalize(req.phone()));
        owner.setPasswordHash(encoder.encode(req.password()));
        owner.setRole(Role.OWNER);
        owner = users.save(owner);

        clinicSetup.createDefaults(clinic);
        return response(owner, clinic);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest req) {
        AppUser user = users.findByEmailIgnoreCase(req.email().trim())
                .filter(AppUser::isActive)
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Wrong email or password"));
        Clinic clinic = clinics.findById(user.getClinicId()).orElseThrow(() -> ApiException.notFound("Clinic"));
        return response(user, clinic);
    }

    @Transactional(readOnly = true)
    public AuthResponse me() {
        AppUser user = users.findById(CurrentUser.userId()).orElseThrow(() -> ApiException.notFound("User"));
        Clinic clinic = clinics.findById(user.getClinicId()).orElseThrow(() -> ApiException.notFound("Clinic"));
        return new AuthResponse(null, UserView.of(user), summary(clinic));
    }

    @Transactional(readOnly = true)
    public List<UserView> listStaff() {
        return users.findByClinicIdOrderByNameAsc(CurrentUser.clinicId()).stream().map(UserView::of).toList();
    }

    @Transactional
    public UserView createStaff(CreateStaffRequest req) {
        CurrentUser.requireOwner();
        if (users.existsByEmailIgnoreCase(req.email())) {
            throw ApiException.conflict("An account with this email already exists");
        }
        AppUser u = new AppUser();
        u.setClinicId(CurrentUser.clinicId());
        u.setName(req.name().trim());
        u.setEmail(req.email().trim().toLowerCase());
        u.setPhone(Phone.normalize(req.phone()));
        u.setPasswordHash(encoder.encode(req.password()));
        u.setRole(req.role());
        u.setDoctorId(req.doctorId());
        return UserView.of(users.save(u));
    }

    @Transactional
    public UserView setStaffActive(Long id, boolean active) {
        CurrentUser.requireOwner();
        AppUser u = users.findByIdAndClinicId(id, CurrentUser.clinicId()).orElseThrow(() -> ApiException.notFound("User"));
        if (u.getId().equals(CurrentUser.userId())) throw ApiException.badRequest("You can't deactivate yourself");
        u.setActive(active);
        return UserView.of(u);
    }

    private AuthResponse response(AppUser user, Clinic clinic) {
        return new AuthResponse(jwt.issue(user), UserView.of(user), summary(clinic));
    }

    private ClinicSummary summary(Clinic c) {
        return new ClinicSummary(c.getId(), c.getName(), c.getArea(), Phone.pretty(c.getWhatsappNumber()),
                c.isWhatsappConnected(), c.isBotEnabled(), c.getPlan().name());
    }
}
