package com.example.outboxpattern.repository;

import com.example.outboxpattern.entity.PedidoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PedidoRepository extends JpaRepository<PedidoEntity, UUID> {
}
