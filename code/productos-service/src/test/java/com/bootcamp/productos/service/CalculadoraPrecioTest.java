package com.bootcamp.productos.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CalculadoraPrecio: precio final con BigDecimal")
class CalculadoraPrecioTest {

    @ParameterizedTest(name = "base {0} con {1}% -> {2}")
    @CsvSource({
        "25000,  10,  22500.00",   // datos del laboratorio
        "450,    25,  337.50",
        "1800,   0,   1800.00",    // sin descuento
        "19.99,  10,  17.99",      // con double daría 17.991000000000003
        "0.05,   50,  0.03",       // 0.025 -> HALF_UP -> 0.03
        "100,    100, 0.00",       // descuento total
        "999.99, 12.5, 874.99"     // porcentaje con decimales
    })
    @DisplayName("Aplica el porcentaje y redondea a 2 decimales (HALF_UP)")
    void calculaPrecioFinal(String base, String porcentaje, String esperado) {
        BigDecimal resultado = CalculadoraPrecio.precioFinal(new BigDecimal(base), new BigDecimal(porcentaje));

        assertThat(resultado.scale()).isEqualTo(2);
        assertThat(resultado.toPlainString()).isEqualTo(esperado);
    }

    @ParameterizedTest(name = "porcentaje {0} -> rechazado")
    @ValueSource(strings = {"-1", "100.01", "150"})
    @DisplayName("Rechaza porcentajes fuera de 0-100")
    void rechazaPorcentajeInvalido(String porcentaje) {
        assertThat(CalculadoraPrecio.porcentajeValido(new BigDecimal(porcentaje))).isFalse();
        assertThatThrownBy(() -> CalculadoraPrecio.precioFinal(BigDecimal.TEN, new BigDecimal(porcentaje)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
