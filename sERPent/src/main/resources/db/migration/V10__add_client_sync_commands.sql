-- V10__add_client_sync_commands.sql
-- PostgreSQL migration for client sync commands (offline/hybrid support)

CREATE TABLE client_sync_commands (
                                      client_sync_command_id BIGSERIAL PRIMARY KEY,
                                      client_id VARCHAR(120) NOT NULL,
                                      client_operation_id VARCHAR(120) NOT NULL,
                                      command_type VARCHAR(40) NOT NULL,
                                      status VARCHAR(30) NOT NULL,
                                      payload TEXT NOT NULL,
                                      received_at TIMESTAMP NOT NULL DEFAULT NOW(),
                                      processed_at TIMESTAMP,
                                      error_message TEXT,
                                      result_reference_type VARCHAR(30),
                                      result_reference_id BIGINT,

                                      CONSTRAINT ux_client_sync_commands_client_operation
                                          UNIQUE (client_id, client_operation_id)
);

CREATE INDEX idx_client_sync_commands_status
    ON client_sync_commands(status);

CREATE INDEX idx_client_sync_commands_received_at
    ON client_sync_commands(received_at);

CREATE INDEX idx_client_sync_commands_command_type
    ON client_sync_commands(command_type);