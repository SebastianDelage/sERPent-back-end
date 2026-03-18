package com.empresa.serpent.sync.domain.entity;

import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Entity
@Table(
        name = "client_sync_commands",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_client_sync_commands_client_operation",
                        columnNames = {"client_id", "client_operation_id"}
                )
        }
)
public class ClientSyncCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "client_sync_command_id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "client_id", nullable = false, length = 120)
    private String clientId;

    @Column(name = "client_operation_id", nullable = false, length = 120)
    private String clientOperationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 40)
    private SyncCommandType commandType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SyncCommandStatus status;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private LocalDateTime receivedAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "result_reference_type", length = 30)
    private SyncResultReferenceType resultReferenceType;

    @Column(name = "result_reference_id")
    private Long resultReferenceId;
}