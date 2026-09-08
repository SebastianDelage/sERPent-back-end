package com.empresa.serpent.inventory.repository;

import com.empresa.serpent.catalog.domain.entity.ProductEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryMovementEntity;
import com.empresa.serpent.inventory.domain.entity.InventoryStockSnapshotEntity;
import com.empresa.serpent.inventory.domain.entity.WarehouseEntity;
import com.empresa.serpent.inventory.domain.enums.MovementType;
import com.empresa.serpent.inventory.service.StockQueryService;
import com.empresa.serpent.inventory.web.dto.filter.StockFilter;
import com.empresa.serpent.inventory.web.dto.response.StockResponse;
import com.empresa.serpent.reports.repository.projection.StockRowProjection;
import com.empresa.serpent.shared.security.WarehouseScopeService;
import com.empresa.serpent.shared.security.WarehouseScopeService.WarehouseScope;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * CUÁNTAS CONSULTAS CUESTA LEER EL STOCK DE UN DEPÓSITO.
 *
 * <p>Es la lectura más caliente que toca InventoryStockSnapshotEntity: la pantalla de venta
 * pide {@code GET /api/stock?warehouseId=N} al abrirse y de nuevo después de confirmar cada
 * venta, y eso baja TODAS las filas del depósito como entidades gestionadas. No está paginada
 * a propósito —el formulario necesita la lista entera para saber qué hay disponible—, así que
 * el costo crece con el catálogo.
 *
 * <p>POR QUÉ ESTE TEST EXISTE. Mapear {@code last_movement_id} como relación es correcto en
 * teoría y podría ser carísimo en la práctica: un {@code @ManyToOne} es EAGER por defecto en
 * JPA, y EAGER acá significa una consulta por fila. Con el catálogo de la granjita eso
 * convierte una consulta en decenas, en la pantalla donde hay gente esperando. El número tiene
 * que estar medido antes y después del cambio, no supuesto.
 *
 * <p>SE CUENTAN SENTENCIAS PREPARADAS, no consultas lógicas: es lo que efectivamente va a la
 * base. El conteo se hace con el catálogo cargado dos veces con distinta cantidad de filas,
 * porque un número solo no distingue "una consulta" de "una por fila" cuando hay pocas filas.
 */
@DataJpaTest
@Import(StockQueryService.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@DisplayName("Cost of reading a warehouse's stock")
class StockSnapshotQueryCountTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private InventoryStockSnapshotRepository repository;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private StockQueryService stockQueryService;

    /*
      Se simula solo el alcance por depósito. El resto del servicio corre de verdad: lo que este
      test mide es cuántas sentencias manda EL CAMINO REAL, y para eso el camino tiene que ser
      el real. Medir el repositorio suelto dejaría pasar que alguien vuelva a cablear el
      servicio a los finders de entidades sin que ningún test se entere.
    */
    @MockitoBean
    private WarehouseScopeService warehouseScopeService;

    private Statistics statistics;
    private WarehouseEntity warehouse;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        warehouse = entityManager.persistAndFlush(
                WarehouseEntity.builder().name("Central QC").active(true).build());
    }

    /**
     * Deja {@code rows} snapshots en el depósito, cada uno apuntando a un movimiento REAL.
     *
     * <p>Los ids inventados no sirven para medir: la clave foránea del esquema los rechaza, y
     * además un id que no existe no dispararía la carga de la relación que este test mide.
     */
    private int seeded = 0;

    private void seedSnapshots(int rows) {
        for (int i = 0; i < rows; i++) {
            int n = seeded++;
            ProductEntity product = entityManager.persistAndFlush(ProductEntity.builder()
                    .name("Producto QC " + n)
                    .price(new BigDecimal("1000"))
                    .sku("QC_" + n)
                    .active(true)
                    .build());

            InventoryMovementEntity movement = entityManager.persistAndFlush(
                    InventoryMovementEntity.builder()
                            .product(product)
                            .warehouse(warehouse)
                            .movementType(MovementType.IN)
                            .quantity(new BigDecimal("10.000"))
                            .build());

            entityManager.persistAndFlush(InventoryStockSnapshotEntity.builder()
                    .product(product)
                    .warehouse(warehouse)
                    .currentStock(new BigDecimal("10.000"))
                    .lastMovementId(movement.getId())
                    .build());
        }
        entityManager.clear();
    }

    /** Sentencias que manda el camino de ENTIDADES, recorriendo lo que recorría producción. */
    private long statementsLoadingManagedEntities() {
        entityManager.clear();
        statistics.clear();

        List<InventoryStockSnapshotEntity> snapshots =
                repository.findByWarehouseIdIn(List.of(warehouse.getId()));

        /*
          SE RECORRE LO MISMO QUE RECORRÍA PRODUCCIÓN, y esto ya se hizo mal una vez: la primera
          versión de este medidor sumaba currentStock y nada más, así que daba una sentencia y no
          veía las cargas perezosas que el armado del DTO dispara al leer producto y depósito. Un
          medidor que no recorre lo que recorre el código mide otra cosa.
        */
        for (InventoryStockSnapshotEntity snapshot : snapshots) {
            assertThat(snapshot.getProduct().getName()).isNotNull();
            assertThat(snapshot.getWarehouse().getName()).isNotNull();
            assertThat(snapshot.getCurrentStock()).isNotNull();
        }

        return statistics.getPrepareStatementCount();
    }

    /** Sentencias que manda el camino PROYECTADO, leyendo los seis campos que arman el DTO. */
    private long statementsLoadingProjection() {
        entityManager.clear();
        statistics.clear();

        List<StockRowProjection> rows =
                repository.findStockRows(null, false, List.of(warehouse.getId()));

        // Los seis, uno por uno: si alguno resolviera contra una relación en vez de contra una
        // columna, la carga que dispare tiene que quedar contada.
        for (StockRowProjection row : rows) {
            assertThat(row.getProductId()).isNotNull();
            assertThat(row.getProductName()).isNotNull();
            assertThat(row.getWarehouseId()).isNotNull();
            assertThat(row.getWarehouseName()).isNotNull();
            assertThat(row.getCurrentStock()).isNotNull();
            assertThat(row.getWarehouseActive()).isNotNull();
        }

        return statistics.getPrepareStatementCount();
    }

    /** Sentencias que manda GET /api/stock de punta a punta, entrando por el servicio. */
    private long statementsThroughTheService() {
        entityManager.clear();
        statistics.clear();

        List<StockResponse> rows = stockQueryService.getStock(
                new StockFilter(null, warehouse.getId(), null));

        // Se leen los seis campos del DTO: si alguno se resolviera contra una entidad perezosa,
        // la carga quedaría contada acá y no se escaparía como pasó con el primer medidor.
        for (StockResponse row : rows) {
            assertThat(row.productName()).isNotNull();
            assertThat(row.warehouseName()).isNotNull();
            assertThat(row.stock()).isNotNull();
            assertThat(row.warehouseActive()).isNotNull();
        }

        return statistics.getPrepareStatementCount();
    }

    @Test
    @DisplayName("The whole GET /api/stock path costs one statement, whatever the catalogue size")
    void theServicePathDoesNotGrowWithRows() {
        given(warehouseScopeService.resolve(warehouse.getId()))
                .willReturn(new WarehouseScope(false, List.of(warehouse.getId())));

        seedSnapshots(3);
        long withThree = statementsThroughTheService();

        seedSnapshots(7);
        long withTen = statementsThroughTheService();

        assertThat(withThree).as("3 filas, por el servicio").isEqualTo(1);
        assertThat(withTen).as("10 filas, por el servicio").isEqualTo(1);
        assertThat(withTen - withThree).as("costo de las 7 filas de más").isZero();
    }

    @Test
    @DisplayName("The projected read costs the same with 3 rows as with 10")
    void projectedReadDoesNotGrowWithRows() {
        seedSnapshots(3);
        long withThree = statementsLoadingProjection();

        seedSnapshots(7);
        long withTen = statementsLoadingProjection();

        assertThat(withThree).as("3 filas, proyectado").isEqualTo(1);
        assertThat(withTen).as("10 filas, proyectado").isEqualTo(1);

        /*
          LA AFIRMACIÓN QUE IMPORTA: cada fila de más cuesta CERO sentencias. Un número fijo
          solo dice "hoy es 1"; lo que este test tiene que impedir es que el costo vuelva a
          crecer con el catálogo, que es lo que pasaba antes y lo que volvería a pasar si
          alguien cambia la proyección por entidades o le agrega una relación al recorrido.
        */
        assertThat(withTen - withThree)
                .as("sentencias que cuestan las 7 filas de más")
                .isZero();
    }

    @Test
    @DisplayName("Loading managed entities instead still grows one statement per row")
    void managedEntitiesStillGrowWithRows() {
        /*
          POR QUÉ ESTE CASO SIGUE ACÁ aunque getStock ya no use este camino. Primero, porque es
          la medición contra la que se justificó el cambio y conviene que quede corriendo en vez
          de escrita en un comentario. Y segundo, porque los finders que devuelven entidades no
          se borraron: los usan la reconstrucción de snapshots, la reconciliación y la vista
          paginada de Stock, que sigue pagando este costo acotado por el tamaño de página.
        */
        seedSnapshots(3);
        long withThree = statementsLoadingManagedEntities();

        seedSnapshots(7);
        long withTen = statementsLoadingManagedEntities();

        assertThat(withThree).as("3 filas: 1 lista + 3 productos + 1 depósito").isEqualTo(5);
        assertThat(withTen).as("10 filas: 1 lista + 10 productos + 1 depósito").isEqualTo(12);

        long porFila = (withTen - withThree) / (10 - 3);
        assertThat(porFila).as("sentencias por fila de más, con entidades").isEqualTo(1);
    }
}
