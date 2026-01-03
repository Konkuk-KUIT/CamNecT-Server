package CamNecT.CamNecT_Server.global.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(
        info = @Info(
                title = "",
                description = "",
                version = "v1.0.0"
        )
)
@Configuration
@RequiredArgsConstructor
public class SwaggerConfig {
    @Bean
    public GroupedOpenApi groupedOpenApi() {
        String [] paths = {
                //스웨거 문서에 해당하는 API 설정
                "/**"
        };

        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch(paths)
                .build();
    }
}
