package com.empresa.serpent.inventory.service;

import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.inventory.web.dto.request.UpdateWarehouseRequest;
import com.empresa.serpent.inventory.web.mapper.WarehouseMapper;
import com.empresa.serpent.shared.exception.ValidationException;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WarehouseService — deactivation guard")
class WarehouseServiceTest {

    @Mock
    private WarehouseRepository warehouseRepository;

    @Mock
    private UserRepository userRepository;

    private final WarehouseMapper warehouseMapper = Mappers.getMapper(WarehouseMapper.class);

    private WarehouseService warehouseService;

    private static final Long WAREHOUSE_ID = 1L;

    @BeforeEach
    void setUp() {
        warehouseService = new WarehouseService(warehouseRepository, warehouseMapper, userRepository);
    }

    private WarehouseEntity active() {
        return WarehouseEntity.builder().id(WAREHOUSE_ID).name("Central").active(true).build();
    }

    private UpdateWarehouseRequest deactivate() {
        return new UpdateWarehouseRequest("Central", false);
    }

    @Test
    @DisplayName("Refuses to deactivate a warehouse that is someone's only one, naming them")
    void refusesToStrandUsers() {
        when(warehouseRepository.findByName("Central")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(active()));
        when(userRepository.findActiveUsersWhoseOnlyWarehouseIs(WAREHOUSE_ID)).thenReturn(List.of(
                UserEntity.builder().id(2L).username("cajero1").build(),
                UserEntity.builder().id(3L).username("cajero2").build()
        ));

        assertThatThrownBy(() -> warehouseService.update(WAREHOUSE_ID, deactivate()))
                .isInstanceOf(ValidationException.class)
                .hasMessage("No podés desactivar el depósito \"Central\" porque es el único asignado a: "
                        + "cajero1, cajero2. Asignales otro depósito antes de desactivarlo.");

        verify(warehouseRepository, never()).save(any());
    }

    @Test
    @DisplayName("Allows deactivating when nobody depends on it")
    void allowsWhenNobodyDependsOnIt() {
        when(warehouseRepository.findByName("Central")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(active()));
        when(userRepository.findActiveUsersWhoseOnlyWarehouseIs(WAREHOUSE_ID)).thenReturn(List.of());
        when(warehouseRepository.save(any(WarehouseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(warehouseService.update(WAREHOUSE_ID, deactivate()).active()).isFalse();
    }

    @Test
    @DisplayName("Does not run the guard when the update is not a deactivation")
    void skipsGuardWhenNotDeactivating() {
        when(warehouseRepository.findByName("Central")).thenReturn(Optional.empty());
        when(warehouseRepository.findById(WAREHOUSE_ID)).thenReturn(Optional.of(active()));
        when(warehouseRepository.save(any(WarehouseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        warehouseService.update(WAREHOUSE_ID, new UpdateWarehouseRequest("Central", true));

        verify(userRepository, never()).findActiveUsersWhoseOnlyWarehouseIs(any());
    }
}
