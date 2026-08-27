package CamNecT.server.global.common.config;

import CamNecT.server.global.common.auth.AdminRoleInterceptor;
import CamNecT.server.global.common.auth.AuthInterceptor;
import CamNecT.server.global.common.auth.UserIdArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final UserIdArgumentResolver userIdArgumentResolver;
    private final AuthInterceptor authInterceptor;
    private final AdminRoleInterceptor adminRoleInterceptor;

    @Value("${security.interceptor.auth.enabled:true}")
    private boolean authEnabled;

    @Value("${security.interceptor.admin.enabled:true}")
    private boolean adminEnabled;

    @Override
    public void addArgumentResolvers(@NonNull List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(userIdArgumentResolver);
    }

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        if (authEnabled) {
            registry.addInterceptor(authInterceptor)
                    .addPathPatterns("/api/**")
                    .excludePathPatterns(
                            "/api/auth/**",
                            "/swagger-ui/**",
                            "/v3/api-docs/**",
                            "/swagger-ui.html"
                    );
        }
        if (adminEnabled) {
            registry.addInterceptor(adminRoleInterceptor)
                    .addPathPatterns("/api/admin/**")
                    .addPathPatterns("/api/activity/admin/**")
                    .addPathPatterns("/api/v1/reports/admin/**")
                    .addPathPatterns("/api/notifications/event");
        }
    }
}
