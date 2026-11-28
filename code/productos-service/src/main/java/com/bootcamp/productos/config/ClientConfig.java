package com.bootcamp.productos.config;

import com.bootcamp.productos.client.catalog.api.CatalogApi;
import com.bootcamp.productos.client.discount.api.DiscountApi;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * Clientes HTTP generados desde los contratos de catalogo-service y descuento-service.
 *
 * TIMEOUTS: antes no había. Si catálogo o descuento se colgaban, este servicio esperaba
 * para siempre y agotaba sus hilos. Ahora una llamada lenta falla rápido y se maneja
 * (descuento -> 0%, catálogo -> 503).
 */
@Configuration
public class ClientConfig {

    /** RestTemplateBuilder asegura que Micrometer propague el traceId a los otros servicios. */
    @Bean
    public RestTemplate restTemplate(
            RestTemplateBuilder builder,
            @Value("${services.http.connect-timeout:2s}") Duration connectTimeout,
            @Value("${services.http.read-timeout:3s}") Duration readTimeout) {
        return builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
    }

    @Bean
    public CatalogApi catalogApi(RestTemplate restTemplate, @Value("${services.catalog.url}") String url) {
        var apiClient = new com.bootcamp.productos.client.catalog.ApiClient(restTemplate);
        apiClient.setBasePath(url);
        return new CatalogApi(apiClient);
    }

    @Bean
    public DiscountApi discountApi(RestTemplate restTemplate, @Value("${services.discount.url}") String url) {
        var apiClient = new com.bootcamp.productos.client.discount.ApiClient(restTemplate);
        apiClient.setBasePath(url);
        return new DiscountApi(apiClient);
    }
}
