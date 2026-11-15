package com.example.outboxpattern.repository;

import com.example.outboxpattern.entity.OutboxEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface OutboxRepository extends JpaRepository<OutboxEntity, UUID> {

    // FOR UPDATE SKIP LOCKED: se houver mais de uma instância da aplicação,
    // cada uma pega um lote diferente e o mesmo evento não é publicado duas vezes.
    @Query(value = """
            SELECT * FROM outbox
            WHERE status = 'PENDING'
            ORDER BY created_at
            LIMIT :limite
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEntity> buscarPendentesParaPublicar(@Param("limite") int limite);
}
