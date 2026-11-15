package com.example.outboxpattern.service;

import com.example.outboxpattern.PedidoDomain;
import com.example.outboxpattern.queue.PublishService;

public class PedidoService {


    final PublishService publishService;

    public PedidoService(PublishService publishService) {
        this.publishService = publishService;
    }

    public void criarPedido(PedidoDomain pedidoDomain){


    }
}
