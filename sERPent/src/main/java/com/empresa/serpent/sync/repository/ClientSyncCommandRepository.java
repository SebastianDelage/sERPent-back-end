package com.empresa.serpent.sync.repository;

import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClientSyncCommandRepository extends JpaRepository<ClientSyncCommandEntity, Long> {

    Optional<ClientSyncCommandEntity> findByClientIdAndClientOperationId(String clientId, String clientOperationId);
}