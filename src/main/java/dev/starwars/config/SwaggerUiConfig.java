package dev.starwars.config;

import java.io.IOException;
import java.io.InputStream;
import org.springdoc.core.properties.SwaggerUiConfigParameters;
import org.springdoc.core.properties.SwaggerUiConfigProperties;
import org.springdoc.core.properties.SwaggerUiOAuthProperties;
import org.springdoc.core.providers.ObjectMapperProvider;
import org.springdoc.webmvc.ui.SwaggerIndexPageTransformer;
import org.springdoc.webmvc.ui.SwaggerIndexTransformer;
import org.springdoc.webmvc.ui.SwaggerWelcomeCommon;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger UI tweaks for trying the API quickly: a successful {@code POST /api/v1/auth/login} from the page
 * fills in <b>Authorize</b> with the returned token, so the catalog endpoints work without copying it.
 *
 * <p>Replaces springdoc's index transformer (declared {@code @ConditionalOnMissingBean}) and adds a
 * {@code responseInterceptor} to {@code swagger-initializer.js}, the same way springdoc adds its CSRF
 * {@code requestInterceptor}. The token stays in the browser: nothing changes on the server side.
 */
@Configuration(proxyBeanMethods = false)
public class SwaggerUiConfig {

    static final String PRESETS = "presets: [";

    /** Inserted where {@code presets: [} starts, which is indented 4 spaces in the initializer. */
    static final String AUTO_AUTHORIZE_INTERCEPTOR = String.join(
                    "\n",
                    "responseInterceptor: (response) => {",
                    "      const path = response.url ? new URL(response.url, document.location.origin).pathname : '';",
                    "      if (response.ok && path === '%s' && response.body && response.body.accessToken) {",
                    "        window.ui.preauthorizeApiKey('%s', response.body.accessToken);",
                    "      }",
                    "      return response;",
                    "    },",
                    "    ")
            .formatted(OpenApiConfig.LOGIN_PATH, OpenApiConfig.BEARER_SCHEME);

    @Bean
    SwaggerIndexTransformer indexPageTransformer(
            SwaggerUiConfigProperties swaggerUiConfig,
            SwaggerUiOAuthProperties swaggerUiOAuthProperties,
            SwaggerWelcomeCommon swaggerWelcomeCommon,
            ObjectMapperProvider objectMapperProvider) {
        return new AutoAuthorizeIndexTransformer(
                swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
    }

    /** Adds the auto-authorize interceptor to springdoc's default initializer. */
    static final class AutoAuthorizeIndexTransformer extends SwaggerIndexPageTransformer {

        AutoAuthorizeIndexTransformer(
                SwaggerUiConfigProperties swaggerUiConfig,
                SwaggerUiOAuthProperties swaggerUiOAuthProperties,
                SwaggerWelcomeCommon swaggerWelcomeCommon,
                ObjectMapperProvider objectMapperProvider) {
            super(swaggerUiConfig, swaggerUiOAuthProperties, swaggerWelcomeCommon, objectMapperProvider);
        }

        @Override
        protected String defaultTransformations(SwaggerUiConfigParameters parameters, InputStream inputStream)
                throws IOException {
            return addAutoAuthorize(super.defaultTransformations(parameters, inputStream));
        }

        static String addAutoAuthorize(String initializer) {
            int at = initializer.indexOf(PRESETS);
            if (at < 0) {
                return initializer;
            }
            return initializer.substring(0, at) + AUTO_AUTHORIZE_INTERCEPTOR + initializer.substring(at);
        }
    }
}
