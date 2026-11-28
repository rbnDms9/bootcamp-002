package com.bootcamp.funcional;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.notNullValue;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;
import com.atlassian.oai.validator.restassured.OpenApiValidationFilter;
import io.restassured.RestAssured;
import io.restassured.config.JsonConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.path.json.config.JsonPathConfig.NumberReturnType;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Prueba funcional de punta a punta contra el laboratorio desplegado.
 * Cada respuesta se valida además contra products-api.yaml: si lo desplegado
 * no cumple el contrato, la prueba falla aunque el código HTTP sea correcto.
 */
@DisplayName("API de Productos en el laboratorio")
class ProductApiTest {

    private static RequestSpecification api;
    private static OpenApiValidationFilter contrato;

    @BeforeAll
    static void iniciarSesion() {
        // Los decimales como BigDecimal: comparar dinero con double da falsos negativos
        RestAssured.config = RestAssuredConfig.config()
                .jsonConfig(JsonConfig.jsonConfig().numberReturnType(NumberReturnType.BIG_DECIMAL));

        contrato = new OpenApiValidationFilter(OpenApiInteractionValidator
                .createForSpecificationUrl(Path.of(System.getProperty("contrato")).toUri().toString())
                // La cookie la exige Kong/Authelia; aquí sólo nos interesa validar RESPUESTAS
                .withLevelResolver(LevelResolver.create()
                        .withLevel("validation.request", ValidationReport.Level.IGNORE)
                        .build())
                .build());

        api = Laboratorio.autenticado();   // un solo login para toda la clase
    }

    @Test
    @DisplayName("Sin sesión, Authelia bloquea la API (no llega al servicio)")
    void sinSesionNoHayAcceso() {
        given(Laboratorio.anonimo())
                .redirects().follow(false)
        .when()
                .get("/api/v1/products/1")
        .then()
                .statusCode(anyOf(is(302), is(401), is(403)));
    }

    @Test
    @DisplayName("GET /api/v1/products/1 -> 200 con el producto 1")
    void testGetProductById() {
        given(api).filter(contrato)
        .when()
                .get("/api/v1/products/1")
        .then()
                .statusCode(200)
                .contentType("application/json")
                .time(lessThan(2L), TimeUnit.SECONDS)
                .body("id", equalTo(1))
                .body("name", notNullValue());
    }

    @Test
    @DisplayName("El precio final es base - descuento, a 2 decimales")
    void precioFinalCorrecto() {
        Map<String, Object> producto = given(api).filter(contrato)
                .get("/api/v1/products/1")
                .then().statusCode(200)
                .extract().jsonPath().getMap("$");

        assertThat(precio(producto.get("finalPrice")))
                .isEqualByComparingTo(esperado(producto));
    }

    @Nested
    @DisplayName("Listado (antes daba 404 Whitelabel)")
    class Listado {

        @Test
        @DisplayName("GET /api/v1/products?size=2 -> página con máximo 2 productos")
        void listadoPaginado() {
            given(api).filter(contrato)
                    .queryParam("size", 2)
            .when()
                    .get("/api/v1/products")
            .then()
                    .statusCode(200)
                    .body("page", equalTo(0))
                    .body("size", equalTo(2))
                    .body("items.size()", lessThanOrEqualTo(2))
                    .body("items.id", everyItem(notNullValue()));
        }

        @Test
        @DisplayName("Todos los productos del listado tienen el precio final bien calculado")
        void preciosDelListado() {
            List<Map<String, Object>> items = given(api).filter(contrato)
                    .queryParam("size", 100)
                    .get("/api/v1/products")
                    .then().statusCode(200)
                    .extract().jsonPath().getList("items");

            assertThat(items).isNotEmpty().allSatisfy(p ->
                    assertThat(precio(p.get("finalPrice")))
                            .as("finalPrice del producto %s", p.get("id"))
                            .isEqualByComparingTo(esperado(p)));
        }

        @Test
        @DisplayName("Una página fuera de rango regresa vacía, no error")
        void paginaFueraDeRango() {
            given(api).filter(contrato)
                    .queryParam("page", 10000)
            .when()
                    .get("/api/v1/products")
            .then()
                    .statusCode(200)
                    .body("items", hasSize(0));
        }
    }

    @Nested
    @DisplayName("Errores en formato problem+json")
    class Errores {

        @Test
        @DisplayName("Producto inexistente -> 404")
        void productoInexistente() {
            given(api).filter(contrato)
            .when()
                    .get("/api/v1/products/999999")
            .then()
                    .statusCode(404)
                    .contentType("application/problem+json")
                    .body("status", equalTo(404))
                    .body("detail", equalTo("No existe el producto con ID 999999"));
        }

        @ParameterizedTest(name = "{0} -> 400")
        @CsvSource({
            "/api/v1/products/0",
            "/api/v1/products?size=101",
            "/api/v1/products?page=-1"
        })
        @DisplayName("Parámetros fuera del contrato -> 400")
        void parametrosInvalidos(String ruta) {
            given(api).filter(contrato)
            .when()
                    .get(ruta)
            .then()
                    .statusCode(400)
                    .contentType("application/problem+json");
        }
    }

    // ---------- utilidades ----------

    private static BigDecimal precio(Object valor) {
        return new BigDecimal(String.valueOf(valor));
    }

    /** Misma regla que CalculadoraPrecio, calculada de forma independiente. */
    private static BigDecimal esperado(Map<String, Object> p) {
        BigDecimal base = precio(p.get("basePrice"));
        BigDecimal porcentaje = precio(p.get("discountPercentage"));
        return base.subtract(base.multiply(porcentaje).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }
}
