-- Borra lo que creó estres/sembrar.sql (prefijos «estres-» / «estres/»); db/demo queda intacta.
\set ON_ERROR_STOP on

DO $$
BEGIN
  IF current_database() NOT IN ('sheets', 'sheets_estres') THEN
    RAISE EXCEPTION 'limpiar.sql solo corre en la BD local de desarrollo (está en %)', current_database();
  END IF;
END $$;

DELETE FROM analisis WHERE ruta_evidencia LIKE 'estres/%';
DELETE FROM arbol WHERE nombre_especifico LIKE 'estres-%';
DELETE FROM zona_follaje WHERE nombre_zona LIKE 'estres-%';

SELECT (SELECT count(*) FROM arbol) AS arboles,
       (SELECT count(*) FROM zona_follaje) AS zonas,
       (SELECT count(*) FROM analisis) AS analisis;
