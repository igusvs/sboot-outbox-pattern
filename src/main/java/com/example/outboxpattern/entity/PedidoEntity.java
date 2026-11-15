package com.example.outboxpattern.entity;

import com.example.outboxpattern.PedidoDomain;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "pedidos")
public class PedidoEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "pedido_id", length = 36)
    private UUID idPedido;

    @Column(name = "cliente_id", nullable = false, length = 36)
    private String idCliente;

    @Column(name = "data_pedido", nullable = false)
    private LocalDate dataPedido;

    // Exigido pelo JPA
    protected PedidoEntity() {
    }

    public PedidoEntity(PedidoDomain pedido) {
        this.idPedido = pedido.idPedido();
        this.idCliente = pedido.idCliente();
        this.dataPedido = pedido.dataPedido();
    }

    public PedidoDomain toDomain() {
        return new PedidoDomain(idPedido, dataPedido, idCliente);
    }

    public UUID getIdPedido() {
        return idPedido;
    }
}
