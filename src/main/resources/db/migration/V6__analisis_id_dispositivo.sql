-- Réplica del cambio de TT_BKN (docker/postgres/migraciones/2026-11-19_analisis_id_dispositivo.sql).
-- analisis.id_dispositivo: teléfono que capturó cada análisis individual.
-- Nula en registros anteriores si no existía el dato; ON DELETE SET NULL: borrar un dispositivo no borra análisis.
-- Solo desarrollo y tests: producción no ejecuta las migraciones de b-sheet (ver ColumnaDispositivoAnalisis).
ALTER TABLE analisis
    ADD COLUMN IF NOT EXISTS id_dispositivo UUID REFERENCES dispositivo (id_dispositivo) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_analisis_dispositivo ON analisis (id_dispositivo);
