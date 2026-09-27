package com.siletry.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {}

    public record SignupRequest(
            @NotBlank String clinicName,
            String area,
            String city,
            @NotBlank String ownerName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String phone,
            String whatsappNumber) {}

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}

    public record UserView(Long id, String name, String email, String phone, Role role, Long doctorId, boolean active) {
        public static UserView of(AppUser u) {
            return new UserView(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getRole(), u.getDoctorId(), u.isActive());
        }
    }

    public record ClinicSummary(Long id, String name, String area, String whatsappNumber,
                                boolean whatsappConnected, boolean botEnabled, String plan) {}

    public record AuthResponse(String token, UserView user, ClinicSummary clinic) {}

    public record CreateStaffRequest(
            @NotBlank String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8) String password,
            String phone,
            @NotNull Role role,
            Long doctorId) {}
}
