-- Réplica del cambio de TT_BKN (docker/postgres/migraciones/2026-11-17_analisis_id_cuenta.sql).
-- analisis.id_cuenta: cuenta que subió cada análisis (la sincronización de TT_BKN la toma del token).
-- Nula en los análisis anteriores (autor desconocido); ON DELETE SET NULL: borrar una cuenta no borra sus
-- análisis. Idempotente: no falla si la columna o el índice ya existen.
-- Solo desarrollo y tests: producción no ejecuta las migraciones de b-sheet (la BD es de TT_BKN), y b-sheet
-- funciona aunque allí aún no exista la columna (ver ColumnaAutorAnalisis).
ALTER TABLE analisis
    ADD COLUMN IF NOT EXISTS id_cuenta UUID REFERENCES cuenta (id_cuenta) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_analisis_cuenta ON analisis (id_cuenta);
