package CamNecT.server.global.openapi;

import CamNecT.server.CamNecTServerApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = CamNecTServerApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LogoutOpenApiContractTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void logoutDoesNotExposeInternalSessionIdAsRequestParameter() throws Exception {
        String apiDocs = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode logoutOperation = objectMapper.readTree(apiDocs)
                .at("/paths/~1api~1auth~1logout/post");
        assertThat(logoutOperation.isMissingNode()).isFalse();

        JsonNode parameters = logoutOperation.path("parameters");
        boolean exposesSessionId = parameters.isArray()
                && StreamSupport.stream(parameters.spliterator(), false)
                .anyMatch(parameter -> "sessionId".equals(parameter.path("name").asText()));

        assertThat(exposesSessionId)
                .as("JWT sid에서 내부 주입하는 sessionId는 OpenAPI 요청 파라미터가 아니어야 한다")
                .isFalse();
    }
}
