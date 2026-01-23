package io.github.faustofan.admin.shared.web;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * 应用启动信息打印
 * <p>
 * 在应用启动时打印重要的访问地址和配置信息。
 * </p>
 *
 */
@ApplicationScoped
public class StartupInfoPrinter {

    private static final Logger LOGGER = Logger.getLogger(StartupInfoPrinter.class);

    @ConfigProperty(name = "quarkus.http.port", defaultValue = "8080")
    int httpPort;

    @ConfigProperty(name = "quarkus.swagger-ui.path", defaultValue = "/swagger-ui")
    String swaggerUiPath;

    @ConfigProperty(name = "quarkus.smallrye-openapi.path", defaultValue = "/openapi")
    String openApiPath;

    @ConfigProperty(name = "quarkus.application.name", defaultValue = "admin-system")
    String appName;

    @ConfigProperty(name = "quarkus.application.version", defaultValue = "1.0.0")
    String appVersion;

    void printStartupInfo(@Observes StartupEvent event) {
        String banner = """

                ╔═══════════════════════════════════════════════════════════════
                ║                  %s v%s
                ╠═══════════════════════════════════════════════════════════════
                ║  Server running at:
                ║    • Local:      http://localhost:%d
                ╠═══════════════════════════════════════════════════════════════
                ║  API Documentation:
                ║    • Dev UI:     http://localhost:%d/q/dev-ui
                ║    • Swagger UI: http://localhost:%d%s
                ║    • OpenAPI:    http://localhost:%d%s
                ╠═══════════════════════════════════════════════════════════════
                ║  Public APIs (no authentication required):
                ║    • Health:     GET  /q/health
                ║    • Auth:       POST /admin-api/auth/login
                ║    • Captcha:    GET  /admin-api/auth/captcha
                ║    • JVM Stats:  GET  /admin-api/monitor/jvm
                ╚═══════════════════════════════════════════════════════════════
                """;

        LOGGER.infof(
                banner,
                centerText(appName, 12),
                appVersion,
                httpPort,
                httpPort,
                httpPort, swaggerUiPath,
                httpPort, openApiPath
        );
    }

    private String centerText(String text, int width) {
        if (text.length() >= width) {
            return text;
        }
        int padding = (width - text.length()) / 2;
        return " ".repeat(padding) + text + " ".repeat(width - text.length() - padding);
    }
}
