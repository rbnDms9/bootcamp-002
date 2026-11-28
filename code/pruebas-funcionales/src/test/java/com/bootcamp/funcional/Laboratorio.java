package com.bootcamp.funcional;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.fail;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.Map;

/**
 * Configuración y sesión del laboratorio.
 *
 * NUNCA credenciales ni IPs en el código (la contraseña de juanperez sigue en el historial
 * de Git por eso). Todo llega por variables de entorno:
 *   LAB_URL       https://184.196.198.11
 *   LAB_USER      usuario de LLDAP (p. ej. alumnoprueba)
 *   LAB_PASSWORD  su contraseña
 */
final class Laboratorio {

    static final String URL = requerida("LAB_URL");
    static final String COOKIE_SESION = "authelia_session";

    private Laboratorio() {
    }

    /** Petición base SIN sesión: para comprobar que Authelia protege la API. */
    static RequestSpecification anonimo() {
        return new RequestSpecBuilder()
                .setBaseUri(URL)
                .setRelaxedHTTPSValidation()   // laboratorio con certificado autofirmado
                .setAccept(ContentType.JSON)
                .build();
    }

    /**
     * Inicia sesión en Authelia (primer factor; el laboratorio usa one_factor)
     * y devuelve una petición base que ya lleva la cookie de sesión.
     */
    static RequestSpecification autenticado() {
        Response login = given(anonimo())
                .contentType(ContentType.JSON)
                .body(Map.of(
                        "username", requerida("LAB_USER"),
                        "password", requerida("LAB_PASSWORD"),
                        "keepMeLoggedIn", false))
                .post("/authelia/api/firstfactor");

        String sesion = login.getCookie(COOKIE_SESION);
        if (login.statusCode() != 200 || sesion == null) {
            // No se imprime la contraseña: sólo el estado y la respuesta de Authelia
            fail("No se pudo iniciar sesión en Authelia (HTTP " + login.statusCode() + "): "
                    + login.asString()
                    + "\nRevisa LAB_USER/LAB_PASSWORD. Ojo: varios intentos fallidos bloquean al usuario"
                    + " unos minutos (regulation de Authelia).");
        }
        return new RequestSpecBuilder()
                .addRequestSpecification(anonimo())
                .addCookie(COOKIE_SESION, sesion)
                .build();
    }

    private static String requerida(String nombre) {
        String valor = System.getenv(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la variable de entorno " + nombre
                    + ". Ejemplo: export LAB_URL=https://184.196.198.11 LAB_USER=alumnoprueba;"
                    + " read -rs LAB_PASSWORD; export LAB_PASSWORD");
        }
        return valor;
    }
}
