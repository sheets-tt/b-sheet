-- Réplica del cambio de TT_BKN (docker/postgres/migraciones/2026-11-09_analisis_on_delete_cascade.sql).
-- analisis.id_arbol / id_zona eran ON DELETE SET NULL, pero chk_arbol_o_zona exige que un análisis de
-- corteza tenga árbol y uno de follaje, zona: borrar un árbol o una zona con análisis fallaba con
-- violación del CHECK. Con CASCADE se borran sus análisis (y, por la FK existente, sus cromáticos),
-- que es lo que TT_BKN ya hace a mano en ConsultarHistorialUseCase.
ALTER TABLE analisis DROP CONSTRAINT IF EXISTS analisis_id_arbol_fkey;
ALTER TABLE analisis
    ADD CONSTRAINT analisis_id_arbol_fkey FOREIGN KEY (id_arbol) REFERENCES arbol (id_arbol) ON DELETE CASCADE;

ALTER TABLE analisis DROP CONSTRAINT IF EXISTS analisis_id_zona_fkey;
ALTER TABLE analisis
    ADD CONSTRAINT analisis_id_zona_fkey FOREIGN KEY (id_zona) REFERENCES zona_follaje (id_zona) ON DELETE CASCADE;
