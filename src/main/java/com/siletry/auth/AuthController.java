package com.siletry.auth;

import com.siletry.auth.AuthDtos.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/auth/signup")
    public AuthResponse signup(@Valid @RequestBody SignupRequest req) {
        return auth.signup(req);
    }

    @PostMapping("/auth/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return auth.login(req);
    }

    @GetMapping("/auth/me")
    public AuthResponse me() {
        return auth.me();
    }

    @GetMapping("/staff")
    public List<UserView> staff() {
        return auth.listStaff();
    }

    @PostMapping("/staff")
    public UserView createStaff(@Valid @RequestBody CreateStaffRequest req) {
        return auth.createStaff(req);
    }

    @PostMapping("/staff/{id}/deactivate")
    public UserView deactivate(@PathVariable Long id) {
        return auth.setStaffActive(id, false);
    }

    @PostMapping("/staff/{id}/activate")
    public UserView activate(@PathVariable Long id) {
        return auth.setStaffActive(id, true);
    }
}
