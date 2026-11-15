package com.example.outboxpattern.service;

import com.example.outboxpattern.PedidoDomain;
import com.example.outboxpattern.entity.OutboxEntity;
import com.example.outboxpattern.entity.PedidoEntity;
import com.example.outboxpattern.repository.OutboxRepository;
import com.example.outboxpattern.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final OutboxRepository outboxRepository;

    public PedidoService(PedidoRepository pedidoRepository, OutboxRepository outboxRepository) {
        this.pedidoRepository = pedidoRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Outbox: o pedido e o evento são gravados na MESMA transação.
     * Ou os dois são salvos, ou nenhum é. Não há chamada ao SQS aqui;
     * quem publica é o OutboxRelay, lendo os eventos PENDING.
     */
    @Transactional
    public PedidoDomain criarPedido() {
        final PedidoDomain pedidoDomain = PedidoDomain.criar();

        pedidoRepository.save(new PedidoEntity(pedidoDomain));
        outboxRepository.save(OutboxEntity.pendente(pedidoDomain.idPedido()));

        return pedidoDomain;
    }
}
