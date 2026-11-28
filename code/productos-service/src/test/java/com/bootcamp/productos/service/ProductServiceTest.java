package com.bootcamp.productos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bootcamp.productos.client.catalog.api.CatalogApi;
import com.bootcamp.productos.client.catalog.model.CatalogPage;
import com.bootcamp.productos.client.catalog.model.CatalogResponse;
import com.bootcamp.productos.client.discount.api.DiscountApi;
import com.bootcamp.productos.client.discount.model.DiscountResponse;
import com.bootcamp.productos.exception.ProductoNoEncontradoException;
import com.bootcamp.productos.exception.ServicioNoDisponibleException;
import com.bootcamp.productos.model.ProductDetailResponse;
import com.bootcamp.productos.model.ProductPage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Reglas de agregación y resiliencia, sin HTTP real: catálogo y descuento son mocks
 * de los clientes generados desde sus contratos.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService: agregación y resiliencia")
class ProductServiceTest {

    @Mock
    private CatalogApi catalogo;

    @Mock
    private DiscountApi descuentos;

    @InjectMocks
    private ProductService servicio;

    private static CatalogResponse laptop() {
        return new CatalogResponse().productId(1L).name("Laptop Pro 14")
                .basePrice(new BigDecimal("25000.00")).stock(15);
    }

    private static DiscountResponse descuento(String porcentaje) {
        return new DiscountResponse().productId(1L).percentage(new BigDecimal(porcentaje)).description("Promo");
    }

    private static HttpClientErrorException noEncontrado() {
        return HttpClientErrorException.create(HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, null, null);
    }

    @Nested
    @DisplayName("Descuento")
    class Descuento {

        @Test
        @DisplayName("Aplica el descuento vigente")
        void aplicaDescuento() {
            when(catalogo.getCatalogByProductId(1L)).thenReturn(laptop());
            when(descuentos.getDiscountByProductId(1L)).thenReturn(descuento("10"));

            ProductDetailResponse r = servicio.obtenerProducto(1L);

            assertThat(r.getDiscountPercentage()).isEqualByComparingTo("10.00");
            assertThat(r.getFinalPrice()).isEqualByComparingTo("22500.00");
        }

        @Test
        @DisplayName("404 de descuento-service = producto sin descuento (0%)")
        void sinDescuento() {
            when(catalogo.getCatalogByProductId(1L)).thenReturn(laptop());
            when(descuentos.getDiscountByProductId(1L)).thenThrow(noEncontrado());

            ProductDetailResponse r = servicio.obtenerProducto(1L);

            assertThat(r.getDiscountPercentage()).isEqualByComparingTo("0");
            assertThat(r.getFinalPrice()).isEqualByComparingTo(r.getBasePrice());
        }

        @Test
        @DisplayName("descuento-service caído o lento: se vende a precio completo")
        void descuentoNoDisponible() {
            when(catalogo.getCatalogByProductId(1L)).thenReturn(laptop());
            when(descuentos.getDiscountByProductId(1L)).thenThrow(new ResourceAccessException("Read timed out"));

            assertThat(servicio.obtenerProducto(1L).getFinalPrice()).isEqualByComparingTo("25000.00");
        }

        @Test
        @DisplayName("descuento-service responde 500: se vende a precio completo")
        void descuentoConError() {
            when(catalogo.getCatalogByProductId(1L)).thenReturn(laptop());
            when(descuentos.getDiscountByProductId(1L)).thenThrow(
                    HttpServerErrorException.create(HttpStatus.INTERNAL_SERVER_ERROR, "Error", HttpHeaders.EMPTY, null, null));

            assertThat(servicio.obtenerProducto(1L).getDiscountPercentage()).isEqualByComparingTo("0");
        }

        @Test
        @DisplayName("Un porcentaje fuera de 0-100 se ignora (defensa ante datos corruptos)")
        void porcentajeInvalido() {
            when(catalogo.getCatalogByProductId(1L)).thenReturn(laptop());
            when(descuentos.getDiscountByProductId(1L)).thenReturn(descuento("150"));

            assertThat(servicio.obtenerProducto(1L).getFinalPrice()).isEqualByComparingTo("25000.00");
        }
    }

    @Nested
    @DisplayName("Catálogo")
    class Catalogo {

        @Test
        @DisplayName("404 del catálogo -> ProductoNoEncontradoException (y no se consulta descuento)")
        void productoInexistente() {
            when(catalogo.getCatalogByProductId(9L)).thenThrow(noEncontrado());

            assertThatThrownBy(() -> servicio.obtenerProducto(9L))
                    .isInstanceOf(ProductoNoEncontradoException.class)
                    .hasMessageContaining("9");
            verify(descuentos, never()).getDiscountByProductId(9L);
        }

        @Test
        @DisplayName("Catálogo caído -> ServicioNoDisponibleException (503)")
        void catalogoNoDisponible() {
            when(catalogo.getCatalogByProductId(1L)).thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> servicio.obtenerProducto(1L))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }

        @Test
        @DisplayName("Catálogo caído al listar -> ServicioNoDisponibleException (503)")
        void catalogoNoDisponibleAlListar() {
            when(catalogo.listCatalog(0, 20)).thenThrow(new ResourceAccessException("Connection refused"));

            assertThatThrownBy(() -> servicio.listarProductos(0, 20))
                    .isInstanceOf(ServicioNoDisponibleException.class);
        }
    }

    @Test
    @DisplayName("Listado: agrega el descuento de cada producto y conserva la paginación")
    void listado() {
        CatalogResponse mouse = new CatalogResponse().productId(2L).name("Mouse")
                .basePrice(new BigDecimal("450")).stock(120);
        when(catalogo.listCatalog(0, 2)).thenReturn(new CatalogPage()
                .items(List.of(laptop(), mouse)).page(0).size(2).totalElements(3L).totalPages(2));
        when(descuentos.getDiscountByProductId(1L)).thenReturn(descuento("10"));
        when(descuentos.getDiscountByProductId(2L)).thenThrow(noEncontrado());

        ProductPage pagina = servicio.listarProductos(0, 2);

        assertThat(pagina.getItems()).extracting(ProductDetailResponse::getFinalPrice)
                .usingElementComparator(BigDecimal::compareTo)   // 450 == 450.00 para BigDecimal
                .containsExactly(new BigDecimal("22500.00"), new BigDecimal("450.00"));
        assertThat(pagina.getTotalElements()).isEqualTo(3L);
        assertThat(pagina.getTotalPages()).isEqualTo(2);
    }
}
