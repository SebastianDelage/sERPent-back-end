#!/usr/bin/env node
/*
  ===========================================================================
  PARIDAD ENTRE LOS DOS JUEGOS DE MIGRACIONES
  ===========================================================================

  Corre con:  node tools/verificar-paridad-de-migraciones.js

  QUÉ COMPARA
  El esquema FINAL que produce cada juego —db/migration para Postgres y
  db/migration-h2 para desarrollo y tests— y no los archivos. Los dos juegos
  tienen numeraciones distintas y distinta cantidad de archivos a propósito: el
  V1 de H2 es una línea de base aplastada que incluye lo que en Postgres son las
  migraciones V2 a V13. Comparar archivos no diría nada; comparar el resultado, sí.

  QUÉ PROBLEMA RESUELVE
  Un ALTER que se agrega a un juego y se olvida en el otro produce tests en verde
  contra un esquema que no es el de producción. Nada lo detectaba.

  ---------------------------------------------------------------------------
  LO QUE ESTE SCRIPT NO PUEDE HACER, CON NOMBRE Y APELLIDO
  ---------------------------------------------------------------------------

  ES UNA EXPRESIÓN REGULAR SOBRE EL FUENTE, NO UNA LECTURA DE METADATOS. Nunca
  levanta una base: lee texto SQL y lo interpreta. Eso tiene consecuencias
  concretas, y no son hipotéticas — las tres primeras ya ocurrieron:

    · La primera versión solo miraba `ALTER TABLE ... ADD CONSTRAINT` y perdía
      las declaradas adentro del CREATE TABLE, que es la forma que usa H2. Dio
      TRES FALSOS POSITIVOS de una sola vez: fk_sales_warehouse,
      ck_transactions_type y ck_inventory_movements_type, las tres presentes en
      los dos juegos. Corregido: ahora mira `CONSTRAINT <nombre>` en cualquier
      posición.

    · Tampoco procesaba DROP INDEX ni DROP CONSTRAINT, así que un índice borrado
      seguía contando como existente. Corregido.

    · NO COMPARA TIPOS, NULLABILITY, DEFAULTS NI EL CONTENIDO DE UN CHECK. Dos
      columnas que se llamen igual pero una sea NUMERIC(12,3) y la otra
      NUMERIC(19,4) pasan como iguales. Un CHECK con distintos valores de enum
      en cada juego, también.

    · No entiende SQL: no sigue renombres (ALTER ... RENAME), no expande vistas,
      y una sintaxis que no esté prevista se ignora en silencio.

  POR ESO IMPRIME EL TOTAL RELEVADO, no solo las diferencias. Si el número de
  tablas o columnas cae de golpe sin que se hayan borrado, el script se quedó
  ciego con alguna forma nueva de escribir SQL, y eso hay que verlo. Un cero en
  "diferencias" no prueba paridad: prueba que no hay diferencias DE LAS QUE VE.

  La versión que sí leería metadatos tendría que levantar las dos bases y
  comparar INFORMATION_SCHEMA. Para H2 eso es un test en proceso; para Postgres
  hace falta Testcontainers, que necesita Docker.
  ===========================================================================
*/
const fs = require('fs');
const path = require('path');

const BASE = path.resolve(__dirname, '../sERPent/src/main/resources/db');

/*
  ---------------------------------------------------------------------------
  LAS DIFERENCIAS LEGÍTIMAS SE DECLARAN ACÁ, UNA POR UNA
  ---------------------------------------------------------------------------
  Nada se ignora solo. Si mañana aparece una diferencia real que además sea
  correcta, hay que escribirla en esta lista con su motivo — y esa fricción es
  deliberada: obliga a decidir si es legítima en vez de a acostumbrarse a un
  reporte que siempre trae ruido.
*/
const LEGITIMAS = {
  indices: {
    ux_product_suppliers_preferred_per_product:
      'Índice único PARCIAL (WHERE preferred = TRUE AND active = TRUE). H2 no soporta ' +
      'índices parciales, así que en desarrollo la unicidad del proveedor preferido la ' +
      'sostiene solo el código. En producción la sostiene la base.',
  },
  tablas: {},
  columnas: {},
  constraints: {},
};

function archivosDe(dir) {
  const d = path.join(BASE, dir);
  return fs
    .readdirSync(d)
    .sort((a, b) => +a.match(/^V(\d+)/)[1] - +b.match(/^V(\d+)/)[1])
    .map((f) => fs.readFileSync(path.join(d, f), 'utf8'));
}

/** Sin comentarios de línea: un ejemplo comentado no es esquema. */
const sinComentarios = (sql) =>
  sql
    .split('\n')
    .filter((l) => !l.trim().startsWith('--'))
    .join('\n');

function esquema(dir) {
  const tablas = new Map();
  const indices = new Set();
  const constraints = new Set();

  for (const bruto of archivosDe(dir)) {
    const s = sinComentarios(bruto);

    for (const m of s.matchAll(
      /CREATE TABLE\s+(?:IF NOT EXISTS\s+)?(\w+)\s*\(([\s\S]*?)\n\s*\);/gi,
    )) {
      const t = m[1].toLowerCase();
      if (!tablas.has(t)) tablas.set(t, new Set());
      for (const linea of m[2].split('\n')) {
        const c = linea.trim().match(/^(\w+)\s+[A-Za-z]/);
        if (!c) continue;
        const nombre = c[1].toLowerCase();
        if (['constraint', 'primary', 'unique', 'foreign', 'check'].includes(nombre)) continue;
        tablas.get(t).add(nombre);
      }
    }

    for (const m of s.matchAll(/ALTER TABLE\s+(\w+)\s+ADD COLUMN\s+(?:IF NOT EXISTS\s+)?(\w+)/gi)) {
      const t = m[1].toLowerCase();
      if (!tablas.has(t)) tablas.set(t, new Set());
      tablas.get(t).add(m[2].toLowerCase());
    }
    for (const m of s.matchAll(/ALTER TABLE\s+(\w+)\s+DROP COLUMN\s+(?:IF EXISTS\s+)?(\w+)/gi)) {
      tablas.get(m[1].toLowerCase())?.delete(m[2].toLowerCase());
    }
    for (const m of s.matchAll(/DROP TABLE\s+(?:IF EXISTS\s+)?(\w+)/gi)) {
      tablas.delete(m[1].toLowerCase());
    }

    for (const m of s.matchAll(/CREATE\s+(?:UNIQUE\s+)?INDEX\s+(?:IF NOT EXISTS\s+)?(\w+)/gi)) {
      indices.add(m[1].toLowerCase());
    }
    for (const m of s.matchAll(/DROP INDEX\s+(?:IF EXISTS\s+)?(\w+)/gi)) {
      indices.delete(m[1].toLowerCase());
    }

    /*
      En cualquier posición: ALTER TABLE ADD CONSTRAINT y CONSTRAINT dentro del CREATE TABLE.
      El `IF EXISTS` opcional va en el patrón y no en el grupo, porque si no un
      `DROP CONSTRAINT IF EXISTS fk_x` daría de baja a una constraint llamada "if".
    */
    for (const m of s.matchAll(/CONSTRAINT\s+(?:IF\s+(?:NOT\s+)?EXISTS\s+)?(\w+)/gi)) {
      const n = m[1].toLowerCase();
      // DROP CONSTRAINT también matchea acá, así que se distingue por el verbo previo.
      const antes = s.slice(Math.max(0, m.index - 12), m.index).toUpperCase();
      if (antes.includes('DROP')) constraints.delete(n);
      else constraints.add(n);
    }
  }
  return { tablas, indices, constraints };
}

const pg = esquema('migration');
const h2 = esquema('migration-h2');
const soloEn = (a, b) => [...a].filter((x) => !b.has(x)).sort();

const hallazgos = [];
function revisar(categoria, titulo, soloPg, soloH2) {
  for (const [lado, lista] of [
    ['postgres', soloPg],
    ['h2', soloH2],
  ]) {
    for (const x of lista) {
      const motivo = LEGITIMAS[categoria]?.[x];
      if (motivo) continue;
      hallazgos.push({ categoria: titulo, lado, nombre: x });
    }
  }
}

const tPg = new Set(pg.tablas.keys());
const tH2 = new Set(h2.tablas.keys());

let columnasPg = 0;
let columnasH2 = 0;
pg.tablas.forEach((c) => (columnasPg += c.size));
h2.tablas.forEach((c) => (columnasH2 += c.size));

console.log('RELEVADO');
console.log('  tablas       postgres ' + tPg.size + '   h2 ' + tH2.size);
console.log('  columnas     postgres ' + columnasPg + '   h2 ' + columnasH2);
console.log('  índices      postgres ' + pg.indices.size + '   h2 ' + h2.indices.size);
console.log('  constraints  postgres ' + pg.constraints.size + '   h2 ' + h2.constraints.size);

revisar('tablas', 'tabla', soloEn(tPg, tH2), soloEn(tH2, tPg));

for (const t of [...tPg].filter((x) => tH2.has(x)).sort()) {
  revisar(
    'columnas',
    'columna de ' + t,
    soloEn(pg.tablas.get(t), h2.tablas.get(t)),
    soloEn(h2.tablas.get(t), pg.tablas.get(t)),
  );
}
revisar('indices', 'índice', soloEn(pg.indices, h2.indices), soloEn(h2.indices, pg.indices));
revisar(
  'constraints',
  'constraint',
  soloEn(pg.constraints, h2.constraints),
  soloEn(h2.constraints, pg.constraints),
);

const declaradas = Object.values(LEGITIMAS).reduce((n, o) => n + Object.keys(o).length, 0);
console.log('  diferencias declaradas como legítimas: ' + declaradas);
console.log('  diferencias NO declaradas: ' + hallazgos.length);

if (declaradas) {
  console.log('\nLEGÍTIMAS, DECLARADAS');
  for (const [, mapa] of Object.entries(LEGITIMAS)) {
    for (const [n, motivo] of Object.entries(mapa)) {
      console.log('  ' + n);
      console.log('      ' + motivo.replace(/(.{86})\s/g, '$1\n      '));
    }
  }
}

if (hallazgos.length) {
  console.log('\nDIFERENCIAS NO DECLARADAS');
  for (const h of hallazgos) {
    console.log('  ' + h.categoria.padEnd(34) + h.nombre + '   (solo en ' + h.lado + ')');
  }
  console.log(
    '\nO se agrega al otro juego, o se declara en LEGITIMAS de este script con su motivo.',
  );
}

process.exitCode = hallazgos.length === 0 ? 0 : 1;
