# Filtros opcionales en las consultas

**La regla, en una línea: si un parámetro de una `@Query` puede llegar en `null` y se usa dentro
de una función de texto, va envuelto en `CAST(... AS String)` en CADA aparición.**

```java
WHERE (CAST(:name AS String) IS NULL
       OR LOWER(w.name) LIKE LOWER(CONCAT('%', CAST(:name AS String), '%')))
```

En cada aparición, y eso se aprendió equivocándose: la primera versión del arreglo casteaba solo
el `IS NULL` y **siguió fallando igual**, porque la que rompe es la que está adentro del
`CONCAT`. Ver "Cómo funciona el CAST" más abajo.

El resto de este archivo es por qué, y cómo se verifica.

---

## Lo que pasó

Con la app instalada en el local, seis pantallas fallaban al abrirse — depósitos, clientes,
proveedores, medios de pago, terminales y categorías de gasto — con el mismo error:

```
ERROR: no existe la función lower(bytea)
SQLState: 42883
```

No se podía crear un depósito, así que la instalación no se podía ni configurar.

**Fallaba el listado sin término de búsqueda, no la búsqueda.** Escribiendo algo en el buscador
la consulta anda perfecto. Lo que rompía era abrir la pantalla, que es lo que pasa siempre.

## La causa: el tipo del parámetro, no el motor

Cuando un parámetro aparece **solo** dentro de un `IS NULL` y de funciones (`CONCAT`, `LOWER`),
Hibernate no tiene de dónde deducir su tipo. Con un valor adelante usa el tipo del valor; con
`null` no hay valor que mirar, y cae en su tipo por defecto, que viaja al driver como
**binario**. PostgreSQL recibe entonces `'%' || $1 || '%'` con `$1` declarado `bytea`, resuelve
la concatenación de bytea, y no existe `lower()` para ese tipo.

**Cuidado con la explicación fácil.** No es que PostgreSQL no pueda inferir el tipo de un null.
Si fuera eso, el error sería otro:

| lo que manda el driver | lo que dice PostgreSQL |
|---|---|
| tipo sin especificar | `42P18 no se pudo determinar el tipo del parámetro $1` |
| `varchar` (null o no) | anda |
| **binario** | **`42883 no existe la función lower(bytea)`** |

Medido con el driver a mano contra PostgreSQL 18.6: el error del local es la tercera fila. A
Postgres le dijeron "esto es bytea", y le creyó.

## Por qué productos no fallaba

La consulta de productos tiene el mismo `LOWER(... CONCAT ...)`, y una cláusula más:

```java
OR p.barcode = :name
```

Esa comparación contra una **columna mapeada** es la que le daba el tipo al parámetro: de ahí
Hibernate lo deduce, y una vez deducido **todas** las apariciones de ese parámetro viajan como
texto. Por eso productos anda entero, incluido su `LOWER(:name)`, que por sí solo no habría
alcanzado.

O sea que productos andaba gracias a una cláusula que está ahí por otro motivo — buscar por
código de barras. Sacarla lo habría roto sin que nadie asocie una cosa con la otra. Por eso el
`CAST` se agregó **también** a productos y al stock, que tenían la misma suerte: el arreglo es la
regla, no el parche donde duele.

## El arreglo, y cómo funciona el CAST

`CAST(:param AS String)` alrededor del parámetro, en cada aparición.

Lo importante es **qué hace y qué no hace**, porque no es lo que parece. El `CAST` no cambia
cómo Hibernate manda el parámetro: el `?` sigue viajando como binario. Lo que hace es envolver
esa aparición en SQL, y entonces PostgreSQL convierte antes de usarla. O sea que arregla **la
aparición que envuelve, y solo ésa**.

De ahí que castear el `IS NULL` no sirviera de nada: `cast($1 as text) is null` es válido con
cualquier tipo, así que esa aparición nunca fue la que rompía. La que rompe es la del `CONCAT`.
La evidencia es el SQL generado de ese intento fallido:

```sql
where (cast(? as text) is null                              -- casteado, y era el inocente
       or lower(we1_0.name) like lower(('%'||?||'%')))      -- este seguia crudo: 42883
```

Sobre PostgreSQL el cast se emite como `cast(? as text)` — sin límite de longitud, así que no
trunca términos largos. Sobre H2 es igual de inofensivo, y el suite de siempre lo confirma.

Se aplicó a nueve consultas: las siete que fallaban y las dos que funcionaban de casualidad.

## Qué parámetros NO necesitan el CAST

Los que ya se comparan contra una columna mapeada en alguna de sus apariciones:

```java
WHERE (:warehouseId IS NULL OR c.warehouse.id = :warehouseId)
WHERE (:dateFrom IS NULL OR p.paymentDate >= :dateFrom)
```

Acá Hibernate saca el tipo de la columna. Están verificados —hay un test por cada uno— y están
verificados **a propósito**: si algún día esa comparación desaparece en un refactor, el test cae
y se ve ahí, no en el mostrador.

## Cómo se verifica

`NullableFilterQueriesTest` ejecuta cada consulta con su filtro opcional en `null`. No mira
resultados: mira que corra. Tiene dos hijas, porque los agujeros eran dos:

| clase | motor | cuándo corre | qué agarra |
|---|---|---|---|
| `NullableFilterQueriesH2Test` | H2 | siempre, con `./mvnw test` | que la consulta **se ejecute alguna vez** |
| `NullableFilterQueriesPostgresTest` | PostgreSQL local | a pedido, `./mvnw test -Dpgcheck=true` | las divergencias que H2 no ve |

```bash
# con PostgreSQL levantado y DB_USERNAME / DB_PASSWORD en el entorno
./mvnw test -Dpgcheck=true
```

**Agregá una línea acá cada vez que escribas una consulta con un filtro que pueda venir en
null.** Es el único lugar del proyecto donde esa consulta se ejecuta contra el motor real.

## Por qué no hay un script que lo detecte

Existe el antecedente: `tools/verify-common-migrations-portability.js` busca construcciones
prohibidas en el SQL de las migraciones. Acá no sirve el mismo enfoque, y la razón es concreta:
la diferencia entre una consulta rota y una sana **no está en el texto**. `:name IS NULL` y
`:warehouseId IS NULL` se escriben igual; una rompe y la otra no, según si el parámetro aparece
o no comparado contra una columna en otra línea. Evaluar eso es hacer lo que hace Hibernate.

Un grep daría falsos positivos en todas las consultas sanas —que son mayoría— y el ruido
terminaría en que nadie lo mira. El chequeo que sirve es ejecutar la consulta, y eso es lo que
hace el test de arriba.

## Lo que falta

Lo correcto sería **Testcontainers**: un PostgreSQL efímero levantado por el suite, y la
pregunta "¿corriste el chequeo?" desaparece. Necesita Docker, que en esta máquina no está — es
la misma limitación que ya está anotada en `db/common/README.md` para las migraciones. Mientras
tanto, el chequeo manual es el reemplazo honesto: misma verificación, disparada a mano.
