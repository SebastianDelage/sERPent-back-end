sERPent - DOMAIN MAP
--------------------

Este documento resume el mapa conceptual del dominio de sERPent,
incluyendo las entidades principales, sus relaciones y la
responsabilidad de cada una dentro del sistema.


1. IDEA GENERAL DEL DOMINIO
---------------------------

sERPent está pensado como un ERP orientado a operaciones comerciales
con inventario.

El dominio gira alrededor de tres ejes principales:

- operaciones comerciales
- stock e inventario
- catálogo y configuración base

La entidad central del modelo es `Transaction`, porque representa
el hecho económico u operativo principal sobre el cual se apoyan
ventas, gastos, compras y ajustes.

A medida que el proyecto evolucionó, este núcleo también pasó a
soportar devoluciones, transferencias, validación de stock y una
arquitectura híbrida de inventario basada en ledger + snapshot.


2. BLOQUES PRINCIPALES DEL DOMINIO
----------------------------------

El dominio puede dividirse conceptualmente en los siguientes bloques:

A. Núcleo transaccional  
B. Detalle de operaciones  
C. Inventario  
D. Catálogo y abastecimiento  
E. Configuración operativa  
F. Reporting y consultas  
G. Evolución futura


3. NÚCLEO TRANSACCIONAL
-----------------------

3.1 Transaction
---------------

Responsabilidad:
Representa una operación principal del sistema.

Ejemplos:
- venta
- gasto
- compra
- ajuste
- devolución
- transferencia

Campos clave:
- id
- date
- type
- status
- total
- description
- paymentMethod
- createdByUser

Relaciones:
- una transaction puede tener muchos transaction details
- una transaction puede tener una sale
- una transaction puede tener un expense
- una transaction puede tener movimientos de inventario asociados

Interpretación:
`Transaction` es la raíz operativa de los eventos del sistema.


3.2 TransactionType
-------------------

Responsabilidad:
Clasificar el tipo de transacción.

Valores actuales / contemplados por la evolución del sistema:

- SALE
- EXPENSE
- PURCHASE
- ADJUSTMENT
- RETURN
- TRANSFER


3.3 TransactionStatus
---------------------

Responsabilidad:
Representar el estado operativo de la transacción.

Valores actuales / utilizados según la evolución real:

- DRAFT
- PENDING
- CONFIRMED
- CANCELLED

Interpretación:

- DRAFT / PENDING = operación no consolidada
- CONFIRMED = operación real
- CANCELLED = anulado


4. DETALLE DE OPERACIONES
-------------------------

4.1 TransactionDetail
---------------------

Responsabilidad:
Representar cada ítem o línea dentro de una transacción.

Campos clave:

- id
- product
- description
- quantity
- unitPrice
- subtotal

Relaciones:

- muchos details pertenecen a una transaction
- cada detail puede referenciar un product

Interpretación:

TransactionDetail descompone una operación compleja en ítems concretos.

Ejemplo:

Una venta puede tener:

- 1 pollo entero
- 1 pata muslo

Eso se representa con dos transaction details.

Observación:

El subtotal se calcula automáticamente a partir de quantity y unitPrice.


4.2 Sale
--------

Responsabilidad:
Guardar información específica de una venta.

Campos clave:

- customerId
- customerName
- customerDocument
- invoiceNumber
- taxTotal
- cae
- dueDate
- transaction

Relación:

- una sale pertenece a una transaction de tipo SALE

Interpretación:

La venta no reemplaza a la transaction, sino que la complementa con
información comercial/fiscal específica.

Observación actual:

Por ahora el cliente no es una entidad formal del dominio, sino que se
maneja con datos sueltos dentro de Sale.


4.3 Expense
-----------

Responsabilidad:
Guardar información específica de un gasto.

Campos esperados:

- supplier
- expenseCategory
- receiptNumber
- reimbursable
- notes
- transaction

Relación:

- un expense pertenece a una transaction de tipo EXPENSE

Interpretación:

Al igual que sale, expense es una especialización de transaction.


4.4 SaleReturn
--------------

Responsabilidad:
Representar una devolución de venta y su vínculo con la venta original.

Campos conceptuales esperados / ya presentes según implementación:

- originalSale
- transaction
- reason

Interpretación:

La devolución no corrige la venta original "en línea", sino que crea
una nueva operación trazable dentro del sistema.

Impacto:

- genera una nueva transaction
- genera movimientos de inventario de tipo `RETURN_IN`


5. INVENTARIO
-------------

5.1 InventoryMovement
---------------------

Responsabilidad:
Representar un movimiento de stock.

Campos clave:

- id
- movementType
- quantity
- unitCost
- createdAt
- note
- product
- warehouse
- transaction

Relaciones:

- un movement pertenece a un product
- un movement ocurre en un warehouse
- un movement puede estar asociado a una transaction

Interpretación:

El stock no se guarda como un número fijo en product.
El stock se construye a partir de la suma de movimientos.

Esto permite:

- trazabilidad
- auditoría
- historial
- reconstrucción de stock


5.2 MovementType
----------------

Responsabilidad:
Clasificar el tipo de movimiento de inventario.

Valores actuales:

- IN
- OUT
- ADJUSTMENT_IN
- ADJUSTMENT_OUT
- TRANSFER_IN
- TRANSFER_OUT
- RETURN_IN


5.3 Warehouse
-------------

Responsabilidad:
Representar un lugar físico donde se almacena stock.

Ejemplos:

- depósito principal
- cámara
- sucursal con stock

Campos clave:

- id
- name
- active
- createdAt

Relaciones:

- un warehouse puede tener muchos inventory movements

Interpretación:

Warehouse representa ubicación logística, no necesariamente
sucursal comercial.


5.4 StockQueryService
---------------------

Responsabilidad:

Proveer el stock actual del sistema para consultas y reporting.

Estado actual:

El sistema utiliza una arquitectura híbrida:

- ledger histórico → `InventoryMovement`
- snapshot optimizado → `InventoryStockSnapshot`

Consultas típicas:

- stock por producto
- stock por warehouse
- stock por producto + warehouse
- stock agrupado
- low stock

Interpretación:

El snapshot permite consultas rápidas sin perder el historial del ledger.


5.5 StockValidationService
--------------------------

Responsabilidad:

Validar cantidades y disponibilidad antes de operaciones como la venta.

Responsabilidades actuales:

- validar quantity positiva
- validar stock disponible
- validar todos los ítems de una venta

Interpretación:

Es un servicio de dominio/aplicación muy importante porque protege la
consistencia del inventario antes de permitir operaciones de salida.


5.6 InventoryStockSnapshot
--------------------------

Responsabilidad:

Mantener una proyección optimizada del stock actual por producto y depósito.

Entidad implementada:

`InventoryStockSnapshot`

Campos:

- product
- warehouse
- currentStock
- updatedAt
- lastMovementId

Interpretación:

El snapshot **no reemplaza al ledger**.

Su función es acelerar:

- consultas de stock actual
- low stock
- validaciones previas a venta
- reporting

Regla de diseño:

- el movimiento se registra en `InventoryMovement`
- el snapshot se actualiza dentro de la misma transacción

Servicio responsable:

`InventoryStockSnapshotService`

Operaciones principales:

- applyMovement
- applyMovements
- rebuildSnapshots
- reconcileSnapshots
- findSnapshotInconsistencies


6. CATÁLOGO Y ABASTECIMIENTO
----------------------------

6.1 Product
-----------

Responsabilidad:

Representar un producto del catálogo.

Campos clave:

- id
- name
- description
- price
- sku
- active
- createdAt

Campos de configuración de inventario ya implementados:

- minimumStock
- reorderPoint
- reorderQuantity

Interpretación:

Product es la entidad de catálogo usada por ventas, detalles,
movimientos e inventario.

Observación:

Actualmente el SKU es opcional.

Reglas actuales importantes:

- si `minimumStock` es null, el producto no participa en low stock
- `reorderPoint` no debe ser menor que `minimumStock`


6.2 Supplier
------------

Responsabilidad:

Representar un proveedor.

Campos esperados:

- name
- document
- taxCondition
- phone
- email
- address
- notes
- active

Interpretación:

Permite gestionar relaciones de compra y abastecimiento.


6.3 ProductSupplier
-------------------

Responsabilidad:

Modelar la relación entre productos y proveedores.

Campos clave:

- product
- supplier
- costPrice
- preferred
- active
- leadTimeDays

Interpretación:

Permite que un producto tenga varios proveedores y distintos costos.


7. CONFIGURACIÓN OPERATIVA
--------------------------

7.1 User
--------

Responsabilidad:

Representar un usuario del sistema.

Campos clave:

- id
- name
- lastName
- username
- passwordHash
- email
- active
- createdAt

Relaciones:

- una transaction referencia al usuario que la creó


7.2 PaymentMethod
-----------------

Responsabilidad:

Representar un método de pago.

Campos clave:

- id
- name
- active

Relaciones:

- una transaction puede usar un payment method


7.3 ExpenseCategory
-------------------

Responsabilidad:

Clasificar gastos.

Ejemplos:

- insumos
- servicios
- mantenimiento

Relación:

- un expense referencia una expense category


8. REPORTING Y CONSULTAS
------------------------

8.1 Sales Reporting
-------------------

Responsabilidad:

Proveer vistas agregadas de ventas.

Reportes ya implementados:

- sales by product
- sales daily
- sales summary
- sales by payment method


8.2 Inventory Reporting
-----------------------

Responsabilidad:

Extender las capacidades de lectura del inventario.

Estado actual:

El sistema ya cuenta con reporting basado en:

- movimientos de inventario
- snapshot de stock
- consultas agrupadas

Esto permite evolucionar hacia reportes como:

- rotación de inventario
- valorización de stock
- movimientos por producto
- movimientos por warehouse


9. RELACIONES PRINCIPALES DEL DOMINIO
-------------------------------------

Transaction  
1 -> N TransactionDetail  

Transaction  
1 -> 1 Sale  

Transaction  
1 -> 1 Expense  

Transaction  
1 -> 1 SaleReturn  

Transaction  
N -> 1 PaymentMethod  

Transaction  
N -> 1 User  

TransactionDetail  
N -> 1 Product  

InventoryMovement  
N -> 1 Product  

InventoryMovement  
N -> 1 Warehouse  

InventoryMovement  
N -> 1 Transaction  

ProductSupplier  
N -> 1 Product  

ProductSupplier  
N -> 1 Supplier  

Expense  
N -> 1 Supplier  

Expense  
N -> 1 ExpenseCategory


10. CASO DE USO CENTRAL DEL DOMINIO
-----------------------------------

El caso de uso más importante hoy es la venta.

Flujo conceptual:

1. Se registra una venta
2. Se valida usuario
3. Se valida payment method
4. Se valida warehouse
5. Se valida stock
6. Se crea una transaction de tipo SALE
7. Se agregan transaction details
8. Se calcula el total
9. Se crea la sale asociada
10. Se generan inventory movements de tipo OUT
11. Se actualiza el snapshot de stock


11. REGLAS CONCEPTUALES IMPORTANTES
-----------------------------------

- `Transaction` es la raíz operativa del sistema.
- `Sale`, `Expense` y `SaleReturn` son extensiones especializadas de transaction.
- `TransactionDetail` representa ítems concretos.
- El stock histórico se deriva de `InventoryMovement`.
- `InventoryStockSnapshot` es solo un modelo optimizado de lectura.
- `Warehouse` representa logística, no sucursal comercial.
- `Product` no debería obligar SKU para todos los casos.
- La validación de stock debe ocurrir antes de consolidar una venta.
- El ledger de inventario debe seguir siendo la fuente histórica de verdad.


12. EVOLUCIONES FUTURAS DEL DOMINIO
-----------------------------------

El modelo actual permite crecer hacia:

- Branch / sucursales
- Customers como entidad formal
- IdentificationMode en Product
- reposición automática
- costing / inventory valuation
- transformación de productos (Approach D)
- arquitectura híbrida online + offline con sincronización


13. TESTING Y CONSOLIDACIÓN DEL DOMINIO
---------------------------------------

El dominio ya empezó a consolidarse con testing automático.

Áreas cubiertas:

- expense flow
- product service
- supplier service
- purchase flow
- stock queries
- inventory movement
- snapshot reconciliation

Herramientas utilizadas:

- JUnit
- Mockito
- JaCoCo


14. CONCLUSIÓN
--------------

El dominio de sERPent está construido alrededor de una idea central:

registrar operaciones del negocio de forma trazable, modular y
extensible.

La combinación de:

- Transaction
- TransactionDetail
- Sale / Expense / SaleReturn
- InventoryMovement
- InventoryStockSnapshot
- Product
- Warehouse
- StockQueryService
- StockValidationService

forma una base sólida para un ERP minorista con stock, ventas y
movimientos de inventario.

La arquitectura actual permite crecer de forma ordenada sin necesidad
de rehacer el modelo desde cero.