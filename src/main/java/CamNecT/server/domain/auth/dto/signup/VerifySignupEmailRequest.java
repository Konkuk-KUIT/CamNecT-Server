package CamNecT.server.domain.auth.dto.signup;

import jakarta.validation.constraints.*;

public record VerifySignupEmailRequest(
        @Email @NotBlank @Size(max = 255) String email,
        @NotBlank @Pattern(regexp = "\\d{6}") String code,

        @NotBlank @Size(max = 50) String username,
        @NotBlank String password,
        @NotBlank @Size(max = 100) String name,
        @NotNull Agreements agreements
) {
    public record Agreements(
            boolean serviceTerms,
            boolean privacyTerms
    ) {}
}
