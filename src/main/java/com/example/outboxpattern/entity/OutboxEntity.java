package com.example.outboxpattern.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "outbox")
public class OutboxEntity {

    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "id", length = 36)
    private UUID id;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "aggregate_id", nullable = false, length = 36)
    private UUID aggregateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEntity() {
    }

    private OutboxEntity(UUID aggregateId) {
        this.id = UUID.randomUUID();
        this.aggregateId = aggregateId;
        this.status = OutboxStatus.PENDING;
        this.createdAt = Instant.now();
    }

    public static OutboxEntity pendente(UUID aggregateId) {
        return new OutboxEntity(aggregateId);
    }

    public void marcarComoEnviado() {
        this.status = OutboxStatus.SENT;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAggregateId() {
        return aggregateId;
    }

    public OutboxStatus getStatus() {
        return status;
    }
}
