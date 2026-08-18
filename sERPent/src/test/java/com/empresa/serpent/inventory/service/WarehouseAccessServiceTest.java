package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.TerminalEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.TerminalRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.shared.exception.ForbiddenException;
import com.empresa.serpent.shared.exception.NotFoundException;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseAccessService")
class WarehouseAccessServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private TerminalRepository terminalRepository;

    @InjectMocks
    private WarehouseAccessService warehouseAccessService;

    private static final Long ASSIGNED_ID = 1L;
    private static final Long UNASSIGNED_ID = 2L;

    private WarehouseEntity warehouse(Long id, String name, boolean active) {
        return WarehouseEntity.builder().id(id).name(name).active(active).build();
    }

    /** A user assigned to "Central" (id 1) and nothing else. */
    private UserEntity userAssignedToCentral() {
        Set<WarehouseEntity> assigned = new LinkedHashSet<>();
        assigned.add(warehouse(ASSIGNED_ID, "Central", true));

        return UserEntity.builder()
                .id(10L)
                .username("cajero")
                .warehouses(assigned)
                .build();
    }

    @Nested
    @DisplayName("without a terminal")
    class FromRequest {

        @Test
        @DisplayName("resolves the requested warehouse when it is assigned to the user")
        void resolvesAssignedWarehouse() {
            WarehouseEntity central = warehouse(ASSIGNED_ID, "Central", true);
            given(warehouseRepository.findById(ASSIGNED_ID)).willReturn(Optional.of(central));

            WarehouseEntity resolved = warehouseAccessService.resolveForOperation(
                    null, ASSIGNED_ID, userAssignedToCentral());

            assertThat(resolved).isSameAs(central);
        }

        @Test
        @DisplayName("rejects a warehouse that is not among the user's assigned ones")
        void rejectsUnassignedWarehouse() {
            given(warehouseRepository.findById(UNASSIGNED_ID))
                    .willReturn(Optional.of(warehouse(UNASSIGNED_ID, "Sucursal Norte", true)));

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    null, UNASSIGNED_ID, userAssignedToCentral()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No tenés permiso para operar en el depósito \"Sucursal Norte\".");
        }

        @Test
        @DisplayName("rejects a user with no warehouses at all")
        void rejectsUserWithoutWarehouses() {
            given(warehouseRepository.findById(ASSIGNED_ID))
                    .willReturn(Optional.of(warehouse(ASSIGNED_ID, "Central", true)));

            UserEntity orphan = UserEntity.builder()
                    .id(11L).username("sin_deposito").warehouses(new LinkedHashSet<>()).build();

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(null, ASSIGNED_ID, orphan))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessageContaining("No tenés permiso para operar");
        }

        @Test
        @DisplayName("rejects an inactive warehouse before checking the assignment")
        void rejectsInactiveWarehouse() {
            given(warehouseRepository.findById(ASSIGNED_ID))
                    .willReturn(Optional.of(warehouse(ASSIGNED_ID, "Central", false)));

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    null, ASSIGNED_ID, userAssignedToCentral()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("El depósito seleccionado está inactivo.");
        }

        @Test
        @DisplayName("rejects a missing warehouse id when there is no terminal to supply one")
        void rejectsMissingWarehouseId() {
            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    null, null, userAssignedToCentral()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("Tenés que indicar el depósito de la operación.");
        }
    }

    @Nested
    @DisplayName("with a terminal")
    class FromTerminal {

        private TerminalEntity terminal(WarehouseEntity warehouse, boolean active) {
            return TerminalEntity.builder()
                    .id(50L).name("Caja 1").warehouse(warehouse).active(active).build();
        }

        @Test
        @DisplayName("takes the warehouse from the terminal and ignores the one in the request")
        void terminalWins() {
            WarehouseEntity central = warehouse(ASSIGNED_ID, "Central", true);
            given(terminalRepository.findById(50L)).willReturn(Optional.of(terminal(central, true)));

            // The request names a different warehouse on purpose: the terminal must win.
            WarehouseEntity resolved = warehouseAccessService.resolveForOperation(
                    50L, UNASSIGNED_ID, userAssignedToCentral());

            assertThat(resolved).isSameAs(central);
        }

        @Test
        @DisplayName("still rejects when the terminal's warehouse is not assigned to the user")
        void rejectsWhenTerminalWarehouseIsNotAssigned() {
            WarehouseEntity north = warehouse(UNASSIGNED_ID, "Sucursal Norte", true);
            given(terminalRepository.findById(50L)).willReturn(Optional.of(terminal(north, true)));

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    50L, ASSIGNED_ID, userAssignedToCentral()))
                    .isInstanceOf(ForbiddenException.class)
                    .hasMessage("No tenés permiso para operar en el depósito \"Sucursal Norte\".");
        }

        @Test
        @DisplayName("rejects an inactive terminal")
        void rejectsInactiveTerminal() {
            WarehouseEntity central = warehouse(ASSIGNED_ID, "Central", true);
            given(terminalRepository.findById(50L)).willReturn(Optional.of(terminal(central, false)));

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    50L, null, userAssignedToCentral()))
                    .isInstanceOf(ValidationException.class)
                    .hasMessage("La terminal \"Caja 1\" está inactiva.");
        }

        @Test
        @DisplayName("rejects an unknown terminal")
        void rejectsUnknownTerminal() {
            given(terminalRepository.findById(99L)).willReturn(Optional.empty());

            assertThatThrownBy(() -> warehouseAccessService.resolveForOperation(
                    99L, null, userAssignedToCentral()))
                    .isInstanceOf(NotFoundException.class);
        }
    }
}
