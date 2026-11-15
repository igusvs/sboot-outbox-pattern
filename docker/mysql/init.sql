USE outbox;

CREATE TABLE IF NOT EXISTS pedidos (
    pedido_id   BIGINT      NOT NULL AUTO_INCREMENT,
    cliente_id  BIGINT      NOT NULL,
    data_pedido DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (pedido_id),
    INDEX idx_pedidos_cliente_id (cliente_id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS outbox (
    id           CHAR(36)    NOT NULL DEFAULT (UUID()),
    aggregate_id BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    -- Usado pelo job que busca os eventos pendentes em ordem de criação
    INDEX idx_outbox_status_created_at (status, created_at)
) ENGINE = InnoDB;
