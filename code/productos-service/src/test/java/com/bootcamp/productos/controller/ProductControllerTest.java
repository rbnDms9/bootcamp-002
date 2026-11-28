package com.bootcamp.productos.controller;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.bootcamp.productos.exception.ProductoNoEncontradoException;
import com.bootcamp.productos.exception.ServicioNoDisponibleException;
import com.bootcamp.productos.model.ProductDetailResponse;
import com.bootcamp.productos.model.ProductPage;
import com.bootcamp.productos.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/** Capa web: códigos HTTP y formato de errores que promete products-api.yaml. */
@WebMvcTest(ProductController.class)
@DisplayName("API de Productos (contrato products-api.yaml)")
class ProductControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ProductService servicio;

    static ProductDetailResponse laptop() {
        return new ProductDetailResponse().id(1L).name("Laptop Pro 14")
                .basePrice(new BigDecimal("25000.00")).discountPercentage(new BigDecimal("10.00"))
                .finalPrice(new BigDecimal("22500.00")).stock(15);
    }

    @Test
    @DisplayName("200: detalle con precio final")
    void detalle() throws Exception {
        when(servicio.obtenerProducto(1L)).thenReturn(laptop());

        mvc.perform(get("/api/v1/products/{id}", 1))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.finalPrice").value(22500.00))
                .andExpect(jsonPath("$.discountPercentage").value(10.00));
    }

    @Test
    @DisplayName("404: producto inexistente en problem+json")
    void noEncontrado() throws Exception {
        when(servicio.obtenerProducto(999L)).thenThrow(new ProductoNoEncontradoException(999L));

        mvc.perform(get("/api/v1/products/{id}", 999))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("No existe el producto con ID 999"));
    }

    @Test
    @DisplayName("503: catálogo caído, con Retry-After y sin detalles internos")
    void catalogoNoDisponible() throws Exception {
        when(servicio.obtenerProducto(1L)).thenThrow(
                new ServicioNoDisponibleException("catálogo", new RuntimeException("http://catalogo-service:8082 refused")));

        mvc.perform(get("/api/v1/products/{id}", 1))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("El catálogo no está disponible en este momento"));
    }

    @ParameterizedTest(name = "id={0} -> 400")
    @CsvSource({"0", "-5"})
    @DisplayName("400: id menor a 1")
    void idInvalido(String id) throws Exception {
        mvc.perform(get("/api/v1/products/{id}", id))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail", allOf(startsWith("id:"), containsString("1"))));

        verifyNoInteractions(servicio);
    }

    @Test
    @DisplayName("200: listado paginado (lo que antes daba 404 Whitelabel)")
    void listado() throws Exception {
        when(servicio.listarProductos(0, 20)).thenReturn(new ProductPage()
                .items(List.of(laptop())).page(0).size(20).totalElements(1L).totalPages(1));

        mvc.perform(get("/api/v1/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Laptop Pro 14"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("400: tamaño de página mayor a 100")
    void listadoInvalido() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", allOf(startsWith("size:"), containsString("100"))));

        verifyNoInteractions(servicio);
    }
}
