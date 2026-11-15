package com.example.outboxpattern.queue;

import com.example.outboxpattern.PedidoDomain;
import com.example.outboxpattern.entity.OutboxEntity;
import com.example.outboxpattern.entity.PedidoEntity;
import com.example.outboxpattern.repository.OutboxRepository;
import com.example.outboxpattern.repository.PedidoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Lê os eventos PENDING da tabela outbox, publica no SQS e marca como SENT.
 * Entrega "at-least-once": se o SQS aceitar a mensagem e o commit falhar,
 * o evento é publicado de novo. O consumidor precisa ser idempotente (usar idPedido).
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    private static final int TAMANHO_LOTE = 50;

    private final OutboxRepository outboxRepository;
    private final PedidoRepository pedidoRepository;
    private final PublishService publishService;

    public OutboxRelay(OutboxRepository outboxRepository,
                       PedidoRepository pedidoRepository,
                       PublishService publishService) {
        this.outboxRepository = outboxRepository;
        this.pedidoRepository = pedidoRepository;
        this.publishService = publishService;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.delay-ms:5000}")
    @Transactional
    public void publicarPendentes() {
        List<OutboxEntity> pendentes = outboxRepository.buscarPendentesParaPublicar(TAMANHO_LOTE);

        for (OutboxEntity evento : pendentes) {
            try {
                PedidoDomain pedido = pedidoRepository.findById(evento.getAggregateId())
                        .map(PedidoEntity::toDomain)
                        .orElseThrow(() -> new IllegalStateException(
                                "Pedido " + evento.getAggregateId() + " não encontrado"));

                publishService.publish(pedido);
                evento.marcarComoEnviado(); // dirty checking: o UPDATE sai no commit
            } catch (Exception e) {
                // Continua PENDING e será tentado de novo na próxima execução
                log.error("Falha ao publicar evento outbox {}", evento.getId(), e);
            }
        }
    }
}
