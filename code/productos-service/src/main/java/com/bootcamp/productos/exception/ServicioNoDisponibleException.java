package com.bootcamp.productos.exception;

/** Un servicio del que dependemos no responde -> 503 problem+json. */
public class ServicioNoDisponibleException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ServicioNoDisponibleException(String servicio, Throwable causa) {
        super("El " + servicio + " no está disponible en este momento", causa);
    }
}
