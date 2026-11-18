-- Datos de prueba: SOLO desarrollo y tests (application-dev.yml / application-test.yml).
-- Autores de los análisis demo (analisis.id_cuenta, V4). Una segunda experta (campo@sheets.tt, Demo1234!,
-- bcrypt cost 10) le da seguimiento al Oyamel del lago junto con demo@sheets.tt (2 personas) y subió una
-- captura de la Zona lago menor. e5 queda sin autor, como los análisis anteriores a la columna.

INSERT INTO cuenta (id_cuenta, correo, contrasena_hash, rol, estado_cuenta, nombre_completo) VALUES
  ('a0000000-0000-0000-0000-000000000004', 'campo@sheets.tt',
   '$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm', 'Experto de campo', 'Activa', 'Experta de Campo Demo');

-- e6: segunda captura del Oyamel, el 5 de octubre (09:00 CDMX), dentro del rango demo (3 a 8 de octubre).
-- Pasa a ser su último análisis; es "infestado" como e1 para no cambiar el estado del árbol (mapa y
-- "siguen con infestación"). Sin foto ni cromático.
INSERT INTO analisis (id_analisis, ruta_evidencia, latitud_captura, longitud_captura, fecha_captura,
                      fecha_sincronizacion, estado_sincronizacion, tipo_imagen, resultado_clasificacion,
                      confianza, observaciones, id_arbol, id_zona, id_cuenta) VALUES
  ('e0000000-0000-0000-0000-000000000006', 'sin_imagen', 19.420500, -99.181800, '2026-10-05T15:00:00Z',
   '2026-10-05T15:30:00Z', 'sincronizado', 'corteza', 'infestado', 0.86, 'Resina fresca en la base del tronco',
   'b0000000-0000-0000-0000-000000000001', NULL, 'a0000000-0000-0000-0000-000000000004');

-- Oyamel: e6 (campo, 10-05) y e1 (demo, 10-03). Zona lago menor: e3 (demo, 10-04) y e4 (campo, 10-07).
UPDATE analisis SET id_cuenta = 'a0000000-0000-0000-0000-000000000001'
WHERE id_analisis IN ('e0000000-0000-0000-0000-000000000001', 'e0000000-0000-0000-0000-000000000002',
                      'e0000000-0000-0000-0000-000000000003');
UPDATE analisis SET id_cuenta = 'a0000000-0000-0000-0000-000000000004'
WHERE id_analisis = 'e0000000-0000-0000-0000-000000000004';
