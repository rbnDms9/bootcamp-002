package com.bootcamp.productos.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Respuestas de error RFC 9457 (application/problem+json) definidas en products-api.yaml:
 * 400 BadRequest, 404 NotFound, 503 ServiceUnavailable.
 * Nunca se expone el detalle interno (stack trace, URL de otros servicios) al cliente.
 */
@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ManejadorErrores.class);

    /** Segundos sugeridos al cliente antes de reintentar un 503. */
    private static final String REINTENTAR_EN = "5";

    @ExceptionHandler(ProductoNoEncontradoException.class)
    public ProblemDetail productoNoEncontrado(ProductoNoEncontradoException ex) {
        log.warn("Producto no encontrado: {}", ex.getProductId());
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problema.setTitle("Not Found");
        return problema;
    }

    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ProblemDetail> servicioNoDisponible(ServicioNoDisponibleException ex) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problema.setTitle("Service Unavailable");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, REINTENTAR_EN)
                .body(problema);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail parametroInvalido(ConstraintViolationException ex) {
        String detalle = ex.getConstraintViolations().stream()
                .map(ManejadorErrores::describir)
                .sorted()
                .collect(Collectors.joining("; "));
        log.warn("Petición rechazada por el contrato: {}", detalle);
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalle);
        problema.setTitle("Bad Request");
        return problema;
    }

    private static String describir(ConstraintViolation<?> violacion) {
        String ruta = violacion.getPropertyPath().toString();
        String parametro = ruta.substring(ruta.lastIndexOf('.') + 1);
        return parametro + ": " + violacion.getMessage();
    }
}
