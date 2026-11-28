package com.bootcamp.productos.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Regla de negocio del precio final, aislada para probarla sin Spring ni HTTP.
 * Dinero con BigDecimal: con double, 19.99 - 10% da 17.991000000000003.
 */
public final class CalculadoraPrecio {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private CalculadoraPrecio() {
        // Clase utilitaria
    }

    /**
     * precioFinal = base - base * porcentaje / 100, redondeado a 2 decimales (HALF_UP).
     *
     * @param base       precio base (mayor o igual a 0)
     * @param porcentaje descuento entre 0 y 100
     */
    public static BigDecimal precioFinal(BigDecimal base, BigDecimal porcentaje) {
        Objects.requireNonNull(base, "base");
        Objects.requireNonNull(porcentaje, "porcentaje");
        if (porcentaje.signum() < 0 || porcentaje.compareTo(CIEN) > 0) {
            throw new IllegalArgumentException("El porcentaje debe estar entre 0 y 100: " + porcentaje);
        }
        // Se redondea UNA sola vez, al final, para no acumular errores
        BigDecimal descuento = base.multiply(porcentaje).divide(CIEN, 10, RoundingMode.HALF_UP);
        return base.subtract(descuento).setScale(2, RoundingMode.HALF_UP);
    }

    /** true si el porcentaje es usable (0 a 100). */
    public static boolean porcentajeValido(BigDecimal porcentaje) {
        return porcentaje != null && porcentaje.signum() >= 0 && porcentaje.compareTo(CIEN) <= 0;
    }
}
