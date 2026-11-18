-- Índices para "el análisis más reciente de cada árbol / zona" (Sql.joinUltimoAnalisis, usado por
-- GET /arboles, /arboles/{id}, /zonas y /zonas/{id}).
--
-- Sin ellos, PostgreSQL recorre idx_analisis_fecha y descarta las filas de otros lugares: con 20 000
-- análisis y 413 árboles la consulta tardaba ~630 ms y GET /arboles llegaba a p95 de 2.1 s con 20
-- usuarios (estres/resultados). Con estos índices: ~3 ms y p95 de 18 ms.
--
-- Producción no corre Flyway (la BD es de TT_BKN). Allí se crean a mano y sin bloquear escrituras; ver
-- la sección «Pruebas de estrés» del README (CREATE INDEX CONCURRENTLY).
CREATE INDEX IF NOT EXISTS idx_analisis_arbol_fecha
    ON analisis (id_arbol, fecha_captura DESC, id_analisis) WHERE id_arbol IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_analisis_zona_fecha
    ON analisis (id_zona, fecha_captura DESC, id_analisis) WHERE id_zona IS NOT NULL;
