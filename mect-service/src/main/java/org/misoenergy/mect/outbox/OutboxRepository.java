package org.misoenergy.mect.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    @Query("SELECT e FROM OutboxEntry e WHERE e.publishedAt IS NULL ORDER BY e.id")
    List<OutboxEntry> findUnpublished();
}
