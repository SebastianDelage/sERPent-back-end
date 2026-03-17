package com.empresa.serpent.catalog.service;

import com.empresa.serpent.catalog.domain.entity.SupplierEntity;
import com.empresa.serpent.catalog.repository.SupplierRepository;
import com.empresa.serpent.catalog.web.dto.request.SupplierCreateRequest;
import com.empresa.serpent.catalog.web.dto.request.SupplierUpdateRequest;
import com.empresa.serpent.catalog.web.dto.response.SupplierResponse;
import com.empresa.serpent.catalog.web.mapper.SupplierMapper;
import com.empresa.serpent.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupplierServiceTest {

    @Mock
    private SupplierRepository supplierRepository;

    private SupplierMapper supplierMapper;
    private SupplierService supplierService;

    @BeforeEach
    void setUp() {
        supplierMapper = Mappers.getMapper(SupplierMapper.class);
        supplierService = new SupplierService(supplierRepository, supplierMapper);
    }

    @Test
    @DisplayName("Should create supplier successfully")
    void shouldCreateSupplierSuccessfully() {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "Proveedor Central",
                "CUIT",
                "30-12345678-9",
                "Responsable Inscripto",
                "2235551111",
                "proveedor@test.com",
                "  Notas de prueba  ",
                "  Calle 123  ",
                true
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Central")).thenReturn(Optional.empty());

        when(supplierRepository.save(any(SupplierEntity.class))).thenAnswer(invocation -> {
            SupplierEntity entity = invocation.getArgument(0);
            entity.setId(1L);
            entity.setCreatedAt(LocalDateTime.of(2026, 3, 16, 10, 0));
            return entity;
        });

        SupplierResponse response = supplierService.create(request);

        assertNotNull(response);
        assertEquals(1L, response.id());
        assertEquals("Proveedor Central", response.name());
        assertEquals("CUIT", response.documentType());
        assertEquals("30-12345678-9", response.documentNumber());
        assertEquals("Responsable Inscripto", response.taxCondition());
        assertEquals("2235551111", response.phone());
        assertEquals("proveedor@test.com", response.email());
        assertTrue(response.active());
    }

    @Test
    @DisplayName("Should default active to true when null on create")
    void shouldDefaultActiveToTrueWhenNullOnCreate() {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "Proveedor Norte",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Norte")).thenReturn(Optional.empty());

        when(supplierRepository.save(any(SupplierEntity.class))).thenAnswer(invocation -> {
            SupplierEntity entity = invocation.getArgument(0);
            entity.setId(2L);
            return entity;
        });

        SupplierResponse response = supplierService.create(request);

        assertEquals(2L, response.id());
        assertTrue(response.active());
    }

    @Test
    @DisplayName("Should normalize optional blank fields to null on create")
    void shouldNormalizeOptionalBlankFieldsToNullOnCreate() {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "  Proveedor Sur  ",
                "   ",
                "   ",
                "   ",
                "   ",
                "   ",
                "   ",
                "   ",
                true
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Sur")).thenReturn(Optional.empty());

        when(supplierRepository.save(any(SupplierEntity.class))).thenAnswer(invocation -> {
            SupplierEntity entity = invocation.getArgument(0);
            entity.setId(3L);
            return entity;
        });

        SupplierResponse response = supplierService.create(request);

        ArgumentCaptor<SupplierEntity> captor = ArgumentCaptor.forClass(SupplierEntity.class);
        verify(supplierRepository).save(captor.capture());

        SupplierEntity saved = captor.getValue();
        assertEquals("Proveedor Sur", saved.getName());
        assertNull(saved.getDocumentType());
        assertNull(saved.getDocumentNumber());
        assertNull(saved.getTaxCondition());
        assertNull(saved.getPhone());
        assertNull(saved.getEmail());
        assertNull(saved.getAddress());
        assertNull(saved.getNotes());

        assertEquals("Proveedor Sur", response.name());
    }

    @Test
    @DisplayName("Should throw when supplier name already exists on create")
    void shouldThrowWhenSupplierNameAlreadyExistsOnCreate() {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "Proveedor Central",
                null, null, null, null, null, null, null, true
        );

        SupplierEntity existing = SupplierEntity.builder().id(99L).name("Proveedor Central").build();

        when(supplierRepository.findByNameIgnoreCase("Proveedor Central")).thenReturn(Optional.of(existing));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> supplierService.create(request)
        );

        assertEquals("Supplier name already exists: Proveedor Central", ex.getMessage());
        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when supplier name is blank")
    void shouldThrowWhenSupplierNameIsBlank() {
        SupplierCreateRequest request = new SupplierCreateRequest(
                "   ",
                null, null, null, null, null, null, null, true
        );

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> supplierService.create(request)
        );

        assertEquals("Supplier name cannot be blank", ex.getMessage());
        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should update supplier successfully")
    void shouldUpdateSupplierSuccessfully() {
        SupplierEntity existing = SupplierEntity.builder()
                .id(1L)
                .name("Proveedor Central")
                .documentType("CUIT")
                .documentNumber("30-12345678-9")
                .taxCondition("Responsable Inscripto")
                .phone("2235551111")
                .email("proveedor@test.com")
                .address("Calle 123")
                .notes("Notas")
                .active(true)
                .build();

        SupplierUpdateRequest request = new SupplierUpdateRequest(
                "Proveedor Premium",
                "CUIT",
                "30-99999999-9",
                "Monotributo",
                "2234442222",
                "nuevo@test.com",
                "  Nuevas notas  ",
                "  Nueva dirección  ",
                false
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Premium")).thenReturn(Optional.empty());
        when(supplierRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(supplierRepository.save(any(SupplierEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SupplierResponse response = supplierService.update(1L, request);

        assertEquals("Proveedor Premium", response.name());
        assertEquals("CUIT", response.documentType());
        assertEquals("30-99999999-9", response.documentNumber());
        assertEquals("Monotributo", response.taxCondition());
        assertEquals("2234442222", response.phone());
        assertEquals("nuevo@test.com", response.email());
        assertFalse(response.active());
    }

    @Test
    @DisplayName("Should throw when supplier name already exists on update for another supplier")
    void shouldThrowWhenSupplierNameAlreadyExistsOnUpdateForAnotherSupplier() {
        SupplierEntity other = SupplierEntity.builder().id(2L).name("Proveedor Norte").build();

        SupplierUpdateRequest request = new SupplierUpdateRequest(
                "Proveedor Norte",
                null, null, null, null, null, null, null, true
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Norte")).thenReturn(Optional.of(other));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> supplierService.update(1L, request)
        );

        assertEquals("Supplier name already exists: Proveedor Norte", ex.getMessage());
        verify(supplierRepository, never()).findById(any());
        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should throw when supplier not found on update")
    void shouldThrowWhenSupplierNotFoundOnUpdate() {
        SupplierUpdateRequest request = new SupplierUpdateRequest(
                "Proveedor Central",
                null, null, null, null, null, null, null, true
        );

        when(supplierRepository.findByNameIgnoreCase("Proveedor Central")).thenReturn(Optional.empty());
        when(supplierRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> supplierService.update(1L, request)
        );

        assertEquals("Supplier not found: 1", ex.getMessage());
        verify(supplierRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should find supplier by id")
    void shouldFindSupplierById() {
        SupplierEntity supplier = SupplierEntity.builder()
                .id(1L)
                .name("Proveedor Central")
                .documentType("CUIT")
                .documentNumber("30-12345678-9")
                .taxCondition("Responsable Inscripto")
                .phone("2235551111")
                .email("proveedor@test.com")
                .active(true)
                .build();

        when(supplierRepository.findById(1L)).thenReturn(Optional.of(supplier));

        SupplierResponse response = supplierService.findById(1L);

        assertEquals(1L, response.id());
        assertEquals("Proveedor Central", response.name());
        assertEquals("CUIT", response.documentType());
    }

    @Test
    @DisplayName("Should throw when supplier not found by id")
    void shouldThrowWhenSupplierNotFoundById() {
        when(supplierRepository.findById(1L)).thenReturn(Optional.empty());

        NotFoundException ex = assertThrows(
                NotFoundException.class,
                () -> supplierService.findById(1L)
        );

        assertEquals("Supplier not found: 1", ex.getMessage());
    }

    @Test
    @DisplayName("Should return all active suppliers")
    void shouldReturnAllActiveSuppliers() {
        SupplierEntity s1 = SupplierEntity.builder().id(1L).name("Proveedor Central").active(true).build();
        SupplierEntity s2 = SupplierEntity.builder().id(2L).name("Proveedor Norte").active(true).build();

        when(supplierRepository.findByActiveTrue()).thenReturn(List.of(s1, s2));

        List<SupplierResponse> result = supplierService.findAllActive();

        assertEquals(2, result.size());
        assertEquals("Proveedor Central", result.get(0).name());
        assertEquals("Proveedor Norte", result.get(1).name());
    }

    @Test
    @DisplayName("Should search active suppliers by name")
    void shouldSearchActiveSuppliersByName() {
        SupplierEntity s1 = SupplierEntity.builder().id(1L).name("Proveedor Central").active(true).build();

        when(supplierRepository.findByActiveTrueAndNameContainingIgnoreCase("central"))
                .thenReturn(List.of(s1));

        List<SupplierResponse> result = supplierService.searchActiveByName("central");

        assertEquals(1, result.size());
        assertEquals("Proveedor Central", result.get(0).name());
    }

    @Test
    @DisplayName("Should return all active suppliers when search name is blank")
    void shouldReturnAllActiveSuppliersWhenSearchNameIsBlank() {
        SupplierEntity s1 = SupplierEntity.builder().id(1L).name("Proveedor Central").active(true).build();
        SupplierEntity s2 = SupplierEntity.builder().id(2L).name("Proveedor Norte").active(true).build();

        when(supplierRepository.findByActiveTrue()).thenReturn(List.of(s1, s2));

        List<SupplierResponse> result = supplierService.searchActiveByName("   ");

        assertEquals(2, result.size());
        verify(supplierRepository).findByActiveTrue();
        verify(supplierRepository, never()).findByActiveTrueAndNameContainingIgnoreCase(any());
    }
}