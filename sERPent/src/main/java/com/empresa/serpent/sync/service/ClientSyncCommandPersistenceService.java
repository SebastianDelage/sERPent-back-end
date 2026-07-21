package com.empresa.serpent.sync.service;

import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a sync command in its OWN transaction (REQUIRES_NEW).
 *
 * <p>Used for the two writes that must NOT share the processing transaction: the initial RECEIVED
 * insert (so the row exists for dedup even if processing later rolls back) and the terminal
 * REJECTED/FAILED marking (written after the processing transaction has already rolled back). The
 * success path — creating the sale/expense and marking PROCESSED — deliberately does NOT go through
 * here: it stays in a single transaction inside {@code SyncCommandResultService}.
 */
@Service
@RequiredArgsConstructor
public class ClientSyncCommandPersistenceService {

    private final ClientSyncCommandRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClientSyncCommandEntity save(ClientSyncCommandEntity command) {
        return repository.save(command);
    }
}