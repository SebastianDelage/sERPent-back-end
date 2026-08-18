package com.empresa.serpent.sync.service;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.catalog.repository.ProductRepository;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.repository.InventoryMovementRepository;
import com.empresa.serpent.inventory.repository.InventoryStockSnapshotRepository;
import com.empresa.serpent.inventory.repository.WarehouseRepository;
import com.empresa.serpent.sync.domain.entity.ClientSyncCommandEntity;
import com.empresa.serpent.sync.domain.enums.SyncCommandStatus;
import com.empresa.serpent.sync.domain.enums.SyncCommandType;
import com.empresa.serpent.sync.domain.enums.SyncResultReferenceType;
import com.empresa.serpent.sync.repository.ClientSyncCommandRepository;
import com.empresa.serpent.sync.web.dto.request.SyncCommandRequest;
import com.empresa.serpent.sync.web.dto.response.SyncCommandResponse;
import com.empresa.serpent.transactions.domain.entity.SaleEntity;
import com.empresa.serpent.transactions.domain.entity.PaymentMethodEntity;
import com.empresa.serpent.transactions.repository.PaymentMethodRepository;
import com.empresa.serpent.transactions.repository.SaleRepository;
import com.empresa.serpent.transactions.repository.TransactionRepository;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleItemRequest;
import com.empresa.serpent.transactions.web.dto.request.CreateSaleRequest;
import com.empresa.serpent.users.domain.entity.UserEntity;
import com.empresa.serpent.users.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for the single-transaction guarantee of the sync command flow (defect 2).
 *
 * <p>Forces a failure at the exact PROCESSED-marking step, inside the real physical transaction of
 * {@code SyncCommandResultService.processCreateSale} (not with a mock that fakes a rollback). It
 * then asserts that the already-created sale is truly rolled back (no orphan) and that a retry
 * reprocesses cleanly, producing exactly one sale.
 *
 * <p>Runs on an in-memory H2 (forced with {@code @AutoConfigureTestDatabase}) so it never touches a
 * real datasource.
 */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class SyncCommandAtomicityTest {

    @MockitoSpyBean
    private ClientSyncCommandRepository clientSyncCommandRepository;

    @Autowired
    private SyncCommandApplicationService syncCommandApplicationService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private WarehouseRepository warehouseRepository;
    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private InventoryStockSnapshotRepository snapshotRepository;
    @Autowired
    private InventoryMovementRepository movementRepository;
    @Autowired
    private SaleRepository saleRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private PaymentMethodRepository paymentMethodRepository;

    private static final String INVOICE = "SYNC-IT-1";
    private static final String CLIENT_ID = "device-IT";
    private static final String OPERATION_ID = "op-IT-1";

    private Long userId;
    private Long warehouseId;
    private Long productId;
    private Long paymentMethodId;

    @BeforeEach
    void setUp() {
        cleanUp();

        WarehouseEntity warehouse = warehouseRepository.save(WarehouseEntity.builder()
                .name("IT warehouse").active(true).build());
        // The sync path validates the warehouse against the acting user's assignment, so the
        // user has to actually be assigned to the warehouse the offline sale registers in.
        UserEntity user = userRepository.save(UserEntity.builder()
                .name("Admin").username("admin_it").passwordHash("hash").active(true)
                .warehouses(new LinkedHashSet<>(Set.of(warehouse))).build());
        ProductEntity product = productRepository.save(ProductEntity.builder()
                .name("Pollo entero").price(new BigDecimal("4500")).sku("IT_POLLO").active(true).build());

        // A sale now requires a payment method, offline path included.
        PaymentMethodEntity paymentMethod = paymentMethodRepository.save(PaymentMethodEntity.builder()
                .name("IT cash").active(true).build());

        userId = user.getId();
        warehouseId = warehouse.getId();
        productId = product.getId();
        paymentMethodId = paymentMethod.getId();

        snapshotRepository.save(InventoryStockSnapshotEntity.builder()
                .product(product).warehouse(warehouse)
                .currentStock(new BigDecimal("100.000")).lastMovementId(null).build());
    }

    @AfterEach
    void tearDown() {
        Mockito.reset(clientSyncCommandRepository);
        cleanUp();
    }

    private void cleanUp() {
        clientSyncCommandRepository.deleteAll();
        movementRepository.deleteAll();
        saleRepository.deleteAll();
        transactionRepository.deleteAll();
        snapshotRepository.deleteAll();
        productRepository.deleteAll();
        // Before warehouses: user_warehouses rows reference them, and the cascade only runs
        // from the user side.
        userRepository.deleteAll();
        warehouseRepository.deleteAll();
        // After transactions: they reference the payment method.
        paymentMethodRepository.deleteAll();
    }

    private SyncCommandRequest saleCommand() throws Exception {
        CreateSaleRequest sale = new CreateSaleRequest(
                null, "Consumidor Final", null, INVOICE, paymentMethodId, userId, warehouseId, "Offline sale",
                List.of(new CreateSaleItemRequest(productId, "Pollo entero",
                        new BigDecimal("5.000"), new BigDecimal("4500.0000"))));
        return new SyncCommandRequest(CLIENT_ID, OPERATION_ID,
                SyncCommandType.CREATE_SALE, objectMapper.writeValueAsString(sale));
    }

    @Test
    @DisplayName("(b) A failure at the PROCESSED marking rolls back the sale (no orphan); a retry reprocesses to exactly one sale")
    void failureWhileMarkingProcessedRollsBackTheSale() throws Exception {
        SyncCommandRequest request = saleCommand();

        // Force the failure at the exact marking step: throw only when the command is saved as
        // PROCESSED, inside the real transaction that also created the sale. Every other save
        // (RECEIVED insert, FAILED marking) is left to the spy's default real behavior.
        Mockito.doThrow(new RuntimeException("boom while marking PROCESSED"))
                .when(clientSyncCommandRepository)
                .save(Mockito.argThat(entity ->
                        entity != null && entity.getStatus() == SyncCommandStatus.PROCESSED));

        SyncCommandResponse firstResponse = syncCommandApplicationService.process(request);

        // The command ends FAILED (retriable) with no result reference...
        assertThat(firstResponse.status()).isEqualTo(SyncCommandStatus.FAILED);
        assertThat(firstResponse.resultReferenceId()).isNull();
        // ...the sale created inside that transaction was rolled back (no orphan)...
        assertThat(saleRepository.findByInvoiceNumber(INVOICE)).isEmpty();
        // ...and the stock decrement was rolled back too.
        assertThat(currentStock()).isEqualByComparingTo("100.000");

        ClientSyncCommandEntity afterFailure = clientSyncCommandRepository
                .findByClientIdAndClientOperationId(CLIENT_ID, OPERATION_ID).orElseThrow();
        assertThat(afterFailure.getStatus()).isEqualTo(SyncCommandStatus.FAILED);
        assertThat(afterFailure.getResultReferenceId()).isNull();

        // Retry with the marking working normally: the FAILED command reprocesses on the same row.
        Mockito.reset(clientSyncCommandRepository);

        SyncCommandResponse retryResponse = syncCommandApplicationService.process(request);

        assertThat(retryResponse.status()).isEqualTo(SyncCommandStatus.PROCESSED);
        assertThat(retryResponse.resultReferenceType()).isEqualTo(SyncResultReferenceType.SALE);
        assertThat(retryResponse.resultReferenceId()).isNotNull();

        // Exactly one sale exists (no duplicate from the first, rolled-back attempt).
        List<SaleEntity> sales = saleRepository.findAll();
        assertThat(sales).hasSize(1);
        Optional<SaleEntity> persistedSale = saleRepository.findByInvoiceNumber(INVOICE);
        assertThat(persistedSale).isPresent();
        assertThat(retryResponse.resultReferenceId()).isEqualTo(persistedSale.get().getId());

        assertThat(currentStock()).isEqualByComparingTo("95.000");
    }

    private BigDecimal currentStock() {
        return snapshotRepository.findByProductIdAndWarehouseId(productId, warehouseId)
                .orElseThrow()
                .getCurrentStock();
    }
}
