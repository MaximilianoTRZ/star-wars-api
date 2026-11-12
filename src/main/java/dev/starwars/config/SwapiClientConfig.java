package dev.starwars.config;

import dev.starwars.swapi.CachingSwapiClient;
import dev.starwars.swapi.SwapiClient;
import dev.starwars.swapi.SwapiRestClient;
import java.net.http.HttpClient;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Builds the {@link SwapiClient} chain: caching in front of the HTTP client, which takes the base URL and
 * timeouts from {@link SwapiProperties}. The caching decorator is the primary bean.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
public class SwapiClientConfig {

    @Bean
    SwapiRestClient swapiRestClient(RestClient.Builder builder, SwapiProperties properties) {
        return new SwapiRestClient(restClient(builder, properties));
    }

    @Bean
    @Primary
    CachingSwapiClient swapiClient(SwapiRestClient swapiRestClient) {
        return new CachingSwapiClient(swapiRestClient);
    }

    /**
     * Builds the SWAPI {@link RestClient}. Starts from Boot's builder to inherit
     * the Jackson converters and observability.
     */
    public static RestClient restClient(RestClient.Builder builder, SwapiProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        return builder.baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .build();
    }
}
