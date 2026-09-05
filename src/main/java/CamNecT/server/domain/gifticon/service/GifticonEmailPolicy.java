package CamNecT.server.domain.gifticon.service;

import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GifticonEmailPolicy {

    private final Validator validator;

    public String normalize(String email) {
        return email == null || email.isBlank() ? null : email.trim();
    }

    public boolean isValid(String email) {
        return validator.validate(new Address(email)).isEmpty();
    }

    private record Address(@NotBlank @Email @Size(max = 255) String value) {}
}
