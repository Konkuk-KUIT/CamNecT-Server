package CamNecT.server.global.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerSuccessResponseAnnotationTest {

    private static final String BASE_PACKAGE = "CamNecT.server";
    private static final String DEBUG_PACKAGE_SEGMENT = ".debug.";

    @Test
    void everyFrontendRestEndpointDeclaresSwaggerSuccessResponse() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        List<String> missingSwagger = new ArrayList<>();
        List<String> missingSuccessResponse = new ArrayList<>();

        for (var candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> controller = Class.forName(candidate.getBeanClassName());
            if (controller.getPackageName().contains(DEBUG_PACKAGE_SEGMENT)) {
                continue;
            }

            for (Method method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null
                        || isIntentionallyDisabledRefresh(controller, method)) {
                    continue;
                }

                String endpoint = controller.getSimpleName() + "#" + method.getName();
                boolean hasSwagger = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class) != null
                        || AnnotatedElementUtils.findMergedAnnotation(method, ApiResponses.class) != null
                        || method.getAnnotationsByType(ApiResponse.class).length > 0;

                if (!hasSwagger) {
                    missingSwagger.add(endpoint);
                    continue;
                }

                boolean hasSuccessResponse = List.of(method.getAnnotationsByType(ApiResponse.class)).stream()
                        .map(ApiResponse::responseCode)
                        .anyMatch(code -> code.matches("2\\d\\d"));
                if (!hasSuccessResponse) {
                    missingSuccessResponse.add(endpoint);
                }
            }
        }

        missingSwagger.sort(Comparator.naturalOrder());
        missingSuccessResponse.sort(Comparator.naturalOrder());

        assertThat(missingSwagger)
                .as("Swagger annotation이 없는 프론트-서버 REST API")
                .isEmpty();
        assertThat(missingSuccessResponse)
                .as("Swagger 2xx 정상 응답이 없는 프론트-서버 REST API")
                .isEmpty();
    }

    private boolean isIntentionallyDisabledRefresh(Class<?> controller, Method method) {
        return controller.getSimpleName().equals("AuthController") && method.getName().equals("refresh");
    }
}
