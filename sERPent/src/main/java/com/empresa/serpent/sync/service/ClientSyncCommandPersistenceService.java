package com.empresa.serpent.sync.service;

import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientSyncCommandPersistenceService {

    private final ClientSyncCommandRepository repository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ClientSyncCommandEntity save(ClientSyncCommandEntity command) {
        return repository.save(command);
    }
}