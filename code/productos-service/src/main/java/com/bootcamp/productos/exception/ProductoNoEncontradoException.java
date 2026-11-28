package com.bootcamp.productos.exception;

/** El producto no existe en el catálogo -> 404 problem+json. */
public class ProductoNoEncontradoException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final Long productId;

    public ProductoNoEncontradoException(Long productId) {
        super("No existe el producto con ID " + productId);
        this.productId = productId;
    }

    public Long getProductId() {
        return productId;
    }
}
