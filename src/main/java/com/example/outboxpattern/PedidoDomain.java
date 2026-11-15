package com.example.outboxpattern;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public record PedidoDomain(UUID idPedido, LocalDate dataPedido, String idCliente) {

    public PedidoDomain {
        Objects.requireNonNull(idPedido, "idPedido não pode ser nulo");
        Objects.requireNonNull(dataPedido, "dataPedido não pode ser nula");
        Objects.requireNonNull(idCliente, "idCliente não pode ser nulo");
    }

    public static PedidoDomain criar() {
        return new PedidoDomain(
                UUID.randomUUID(),
                LocalDate.now(),
                String.valueOf(ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE))
        );
    }
}
