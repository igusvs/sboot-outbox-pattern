package com.example.outboxpattern.service;

import com.example.outboxpattern.PedidoDomain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Gera pedidos em loop quando a aplicação sobe (útil para testar o outbox).
 * Liga/desliga com: outbox.generator.enabled=true
 */
@Component
@ConditionalOnProperty(name = "outbox.generator.enabled", havingValue = "true")
public class PedidoGenerator {

    private static final Logger log = LoggerFactory.getLogger(PedidoGenerator.class);

    private final PedidoService pedidoService;
    private final long intervaloMs;
    private volatile Thread thread;

    public PedidoGenerator(PedidoService pedidoService,
                           @Value("${outbox.generator.interval-ms:1000}") long intervaloMs) {
        this.pedidoService = pedidoService;
        this.intervaloMs = intervaloMs;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciar() {
        thread = Thread.ofVirtual().name("pedido-generator").start(this::loop);
    }

    private void loop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                // Chamada via bean injetado (proxy do Spring) -> @Transactional funciona
                PedidoDomain pedido = pedidoService.criarPedido();
                log.info("Pedido criado: {}", pedido.idPedido());
                Thread.sleep(intervaloMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("Falha ao criar pedido", e);
            }
        }
    }

    @PreDestroy
    public void parar() {
        if (thread != null) {
            thread.interrupt();
        }
    }
}
