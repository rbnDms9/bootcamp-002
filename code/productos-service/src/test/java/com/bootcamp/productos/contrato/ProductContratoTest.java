package com.bootcamp.productos.contrato;

import static com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers.openApi;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.bootcamp.productos.controller.ProductController;
import com.bootcamp.productos.exception.ProductoNoEncontradoException;
import com.bootcamp.productos.exception.ServicioNoDisponibleException;
import com.bootcamp.productos.model.ProductDetailResponse;
import com.bootcamp.productos.model.ProductPage;
import com.bootcamp.productos.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PRUEBAS DE CONTRATO: cada respuesta se valida contra products-api.yaml.
 * Se ignora la validación de la PETICIÓN (incluida la cookie de sesión, que en el
 * laboratorio exige Kong + Authelia y no este servicio).
 */
@WebMvcTest(ProductController.class)
@DisplayName("Las respuestas cumplen products-api.yaml")
class ProductContratoTest {

    private static final OpenApiInteractionValidator CONTRATO = OpenApiInteractionValidator
            .createForSpecificationUrl(
                    ProductContratoTest.class.getResource("/openapi/products-api.yaml").toString())
            .withLevelResolver(LevelResolver.create()
                    .withLevel("validation.request", ValidationReport.Level.IGNORE)
                    .build())
            .build();

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProductService servicio;

    private static ProductDetailResponse laptop() {
        return new ProductDetailResponse().id(1L).name("Laptop Pro 14")
                .basePrice(new BigDecimal("25000.00")).discountPercentage(new BigDecimal("10.00"))
                .finalPrice(new BigDecimal("22500.00")).stock(15);
    }

    @Test
    @DisplayName("200 del detalle cumple ProductDetailResponse")
    void detalle() throws Exception {
        when(servicio.obtenerProducto(1L)).thenReturn(laptop());
        mvc.perform(get("/api/v1/products/1")).andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("200 del listado cumple ProductPage")
    void listado() throws Exception {
        when(servicio.listarProductos(0, 20)).thenReturn(new ProductPage()
                .items(List.of(laptop())).page(0).size(20).totalElements(1L).totalPages(1));
        mvc.perform(get("/api/v1/products")).andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("404 cumple NotFound (problem+json)")
    void noEncontrado() throws Exception {
        when(servicio.obtenerProducto(999L)).thenThrow(new ProductoNoEncontradoException(999L));
        mvc.perform(get("/api/v1/products/999")).andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("503 cumple ServiceUnavailable (problem+json)")
    void noDisponible() throws Exception {
        when(servicio.obtenerProducto(1L)).thenThrow(new ServicioNoDisponibleException("catálogo", null));
        mvc.perform(get("/api/v1/products/1")).andExpect(openApi().isValid(CONTRATO));
    }

    @Test
    @DisplayName("400 cumple BadRequest (problem+json)")
    void peticionInvalida() throws Exception {
        mvc.perform(get("/api/v1/products/0")).andExpect(openApi().isValid(CONTRATO));
    }
}
