package com.bootcamp.productos.service;

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
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;

/**
 * Agrega catálogo + descuento y calcula el precio final.
 *
 * Reglas de resiliencia (documentadas en products-api.yaml):
 *  - Catálogo responde 404           -> el producto no existe (404).
 *  - Catálogo no responde / falla    -> 503 (sin catálogo no hay nada que mostrar).
 *  - Descuento responde 404          -> 0% (el producto no tiene descuento).
 *  - Descuento no responde / falla   -> 0% (degradación elegante: se vende a precio completo).
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final String CATALOGO = "catálogo";

    private final CatalogApi catalogo;
    private final DiscountApi descuentos;

    public ProductService(CatalogApi catalogo, DiscountApi descuentos) {
        this.catalogo = catalogo;
        this.descuentos = descuentos;
    }

    public ProductDetailResponse obtenerProducto(Long id) {
        log.debug("Consultando catálogo para ID: {}", id);
        CatalogResponse producto;
        try {
            producto = catalogo.getCatalogByProductId(id);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductoNoEncontradoException(id);
        } catch (RestClientException e) {
            log.error("catalogo-service no disponible al consultar el producto {}", id, e);
            throw new ServicioNoDisponibleException(CATALOGO, e);
        }
        return consolidar(producto);
    }

    /**
     * Pagina sobre el catálogo y agrega el descuento de cada producto.
     * Nota de diseño: hace 1 llamada a descuento por producto (patrón N+1). Con size <= 100 es
     * aceptable en el laboratorio; en producción conviene un endpoint de descuentos por lote.
     */
    public ProductPage listarProductos(Integer page, Integer size) {
        CatalogPage pagina;
        try {
            pagina = catalogo.listCatalog(page, size);
        } catch (RestClientException e) {
            log.error("catalogo-service no disponible al listar (página {}, tamaño {})", page, size, e);
            throw new ServicioNoDisponibleException(CATALOGO, e);
        }

        List<ProductDetailResponse> items = pagina.getItems().stream().map(this::consolidar).toList();
        return new ProductPage()
                .items(items)
                .page(pagina.getPage())
                .size(pagina.getSize())
                .totalElements(pagina.getTotalElements())
                .totalPages(pagina.getTotalPages());
    }

    private ProductDetailResponse consolidar(CatalogResponse producto) {
        BigDecimal base = producto.getBasePrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal porcentaje = obtenerDescuento(producto.getProductId()).setScale(2, RoundingMode.HALF_UP);

        return new ProductDetailResponse()
                .id(producto.getProductId())
                .name(producto.getName())
                .basePrice(base)
                .discountPercentage(porcentaje)
                .finalPrice(CalculadoraPrecio.precioFinal(base, porcentaje))
                .stock(producto.getStock());
    }

    /** Nunca lanza excepción: cualquier problema con descuento-service equivale a 0%. */
    private BigDecimal obtenerDescuento(Long id) {
        try {
            DiscountResponse descuento = descuentos.getDiscountByProductId(id);
            BigDecimal porcentaje = descuento == null ? null : descuento.getPercentage();
            if (!CalculadoraPrecio.porcentajeValido(porcentaje)) {
                log.warn("Descuento inválido para el producto {} ({}); se aplica 0%", id, porcentaje);
                return BigDecimal.ZERO;
            }
            log.debug("Descuento de {}% para el producto {}", porcentaje, id);
            return porcentaje;
        } catch (HttpClientErrorException.NotFound e) {
            log.debug("El producto {} no tiene descuento vigente", id);
            return BigDecimal.ZERO;
        } catch (RestClientException e) {
            log.warn("descuento-service no disponible para el producto {}: {}. Se aplica 0%", id, e.getMessage());
            return BigDecimal.ZERO;
        }
    }
}
