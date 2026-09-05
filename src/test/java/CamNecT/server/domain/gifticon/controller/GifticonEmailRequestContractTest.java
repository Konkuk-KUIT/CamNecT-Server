package CamNecT.server.domain.gifticon.controller;

import CamNecT.server.domain.gifticon.dto.request.ConfirmGifticonPurchaseRequest;
import CamNecT.server.domain.gifticon.dto.response.GifticonPurchaseConfirmResponse;
import CamNecT.server.domain.gifticon.service.GifticonPurchaseService;
import CamNecT.server.domain.gifticon.service.GifticonService;
import CamNecT.server.global.common.util.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GifticonEmailRequestContractTest {

    @Test
    void legacyRecipientFieldIsRejectedEvenWhenJacksonIgnoresUnknownProperties() throws Exception {
        var service = mock(GifticonPurchaseService.class);
        var mapper = new ObjectMapper().findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        var mvc = MockMvcBuilders.standaloneSetup(new GifticonController(mock(GifticonService.class), service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper)).build();
        var request = new LinkedHashMap<String, Object>(Map.of(
                "productId", 10, "quantity", 1, "spendPoints", 1000, "clientRequestId", "legacy"));
        request.put("recipientPhone", "01012345678");

        mvc.perform(post("/api/gifticons/purchases/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
        verifyNoInteractions(service);

        request.remove("recipientPhone");
        request.put("recipientEmail", "recipient@example.com");
        when(service.confirm(isNull(), any())).thenReturn(
                new GifticonPurchaseConfirmResponse(1L, LocalDateTime.now()));
        mvc.perform(post("/api/gifticons/purchases/confirm").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk());
        verify(service).confirm(isNull(), eq(new ConfirmGifticonPurchaseRequest(
                10L, 1, 1000, "legacy", null, "recipient@example.com", null)));
    }

    @Test
    void recipientEmailIsOptionalNormalizedAndValidated() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String email : new String[]{null, "", "  ", " recipient@example.com "}) {
                assertThat(validator.validate(request(email))).isEmpty();
            }
            assertThat(request(" recipient@example.com ").recipientEmail()).isEqualTo("recipient@example.com");
            assertThat(request("  ").recipientEmail()).isNull();
            for (String email : new String[]{"01012345678", "not-an-email", "a".repeat(250) + "@example.com"}) {
                assertThat(validator.validate(request(email))).isNotEmpty();
            }
        }
    }

    private ConfirmGifticonPurchaseRequest request(String email) {
        return new ConfirmGifticonPurchaseRequest(10L, 1, 1000, "request", null, email, null);
    }
}
