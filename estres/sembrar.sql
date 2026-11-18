-- Volumen de datos para las pruebas de estrés: SOLO en la BD local de Docker (docker compose, perfil dev).
-- Con los 5 análisis de db/demo las consultas no miden nada; esto agrega árboles, zonas y análisis
-- repartidos en los últimos 90 días. Todo lo creado lleva el prefijo «estres-» / «estres/» para poder
-- borrarlo con limpiar.sql sin tocar db/demo.
--
-- Variables (psql -v): arboles, zonas, analisis. Las pone correr.sh.
\set ON_ERROR_STOP on

DO $$
BEGIN
  IF current_database() NOT IN ('sheets', 'sheets_estres') THEN
    RAISE EXCEPTION 'sembrar.sql solo corre en la BD local de desarrollo (está en %)', current_database();
  END IF;
END $$;

INSERT INTO arbol (nombre_especifico, especie, latitud, longitud, descripcion, id_cuenta)
SELECT 'estres-arbol-' || n,
       (ARRAY['Ahuehuete', 'Oyamel', 'Pino', 'Encino'])[1 + n % 4],
       19.41 + random() * 0.03,
       -99.20 + random() * 0.03,
       'Creado por estres/sembrar.sql',
       'a0000000-0000-0000-0000-000000000002'
FROM generate_series(1, :arboles) AS n;

INSERT INTO zona_follaje (nombre_zona, latitud_centro, longitud_centro, radio_metros, descripcion, id_cuenta)
SELECT 'estres-zona-' || n,
       19.41 + random() * 0.03,
       -99.20 + random() * 0.03,
       50 + random() * 200,
       'Creada por estres/sembrar.sql',
       'a0000000-0000-0000-0000-000000000002'
FROM generate_series(1, :zonas) AS n;

-- La mitad de corteza (en árboles) y la mitad de follaje (en zonas), como exige chk_arbol_o_zona.
WITH arboles AS (SELECT id_arbol, row_number() OVER () AS i FROM arbol WHERE nombre_especifico LIKE 'estres-%'),
     zonas AS (SELECT id_zona, row_number() OVER () AS i FROM zona_follaje WHERE nombre_zona LIKE 'estres-%'),
     totales AS (SELECT (SELECT count(*) FROM arboles) AS na, (SELECT count(*) FROM zonas) AS nz),
     filas AS (
       SELECT n,
              n % 2 = 0 AS corteza,
              now() - (random() * interval '90 days') AS captura,
              random() AS r,
              random() AS c
       FROM generate_series(1, :analisis) AS n
     )
INSERT INTO analisis (ruta_evidencia, latitud_captura, longitud_captura, fecha_captura, fecha_sincronizacion,
                      estado_sincronizacion, tipo_imagen, resultado_clasificacion, confianza, observaciones,
                      id_arbol, id_zona)
SELECT 'estres/' || f.n || '.jpg',
       19.41 + random() * 0.03,
       -99.20 + random() * 0.03,
       f.captura,
       f.captura + interval '10 minutes',
       CASE WHEN f.r < 0.03 THEN 'error' WHEN f.r < 0.06 THEN 'pendiente' ELSE 'sincronizado' END,
       CASE WHEN f.corteza THEN 'corteza' ELSE 'follaje' END,
       CASE WHEN f.r < 0.20 THEN 'infestado' WHEN f.r < 0.30 THEN 'no_determinado' ELSE 'sano' END,
       CASE WHEN f.c < 0.05 THEN NULL ELSE round((0.3 + f.c * 0.7)::numeric, 3) END,
       CASE WHEN f.n % 5 = 0 THEN 'Observación de prueba ' || f.n END,
       CASE WHEN f.corteza THEN (SELECT id_arbol FROM arboles WHERE i = 1 + f.n % t.na) END,
       CASE WHEN NOT f.corteza THEN (SELECT id_zona FROM zonas WHERE i = 1 + f.n % t.nz) END
FROM filas f CROSS JOIN totales t;

SELECT (SELECT count(*) FROM arbol) AS arboles,
       (SELECT count(*) FROM zona_follaje) AS zonas,
       (SELECT count(*) FROM analisis) AS analisis;
