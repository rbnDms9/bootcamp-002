package com.bootcamp.productos.controller;

import com.bootcamp.productos.api.ProductsApi;
import com.bootcamp.productos.model.ProductDetailResponse;
import com.bootcamp.productos.model.ProductPage;
import com.bootcamp.productos.service.ProductService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implementa products-api.yaml (interfaz generada ProductsApi).
 * El controlador sólo traduce HTTP <-> servicio; los errores los resuelve ManejadorErrores.
 */
@RestController
public class ProductController implements ProductsApi {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Override
    public ResponseEntity<ProductDetailResponse> getProductById(Long id) {
        log.info("Detalle de producto solicitado: {}", id);
        return ResponseEntity.ok(productService.obtenerProducto(id));
    }

    @Override
    public ResponseEntity<ProductPage> listProducts(Integer page, Integer size) {
        log.info("Listado de productos solicitado: página {}, tamaño {}", page, size);
        return ResponseEntity.ok(productService.listarProductos(page, size));
    }
}
