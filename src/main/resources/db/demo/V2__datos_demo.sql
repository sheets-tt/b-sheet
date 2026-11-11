-- Datos de prueba: SOLO desarrollo y tests (application-dev.yml / application-test.yml).
-- Contraseña de las tres cuentas: Demo1234! (bcrypt, cost 10). UUIDs fijos para que los tests los usen.

INSERT INTO cuenta (id_cuenta, correo, contrasena_hash, rol, estado_cuenta, nombre_completo) VALUES
  ('a0000000-0000-0000-0000-000000000001', 'demo@sheets.tt',
   '$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm', 'Experto de campo', 'Activa', 'Usuario Demo'),
  ('a0000000-0000-0000-0000-000000000002', 'admin@sheets.tt',
   '$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm', 'Administrador', 'Activa', 'Administrador Demo'),
  ('a0000000-0000-0000-0000-000000000003', 'baja@sheets.tt',
   '$2a$10$z9OACnNPUJvH25YzOGWusur21etn5BNxNX08wpeDclCjvvmUGLEFm', 'Experto de campo', 'desactivada', 'Cuenta Desactivada');

INSERT INTO dispositivo (id_dispositivo, identificador_unico, nombre_dispositivo) VALUES
  ('d0000000-0000-0000-0000-000000000001', 'demo-pixel-7-pro', 'Pixel 7 Pro demo');

INSERT INTO cuenta_dispositivo (id_cuenta, id_dispositivo) VALUES
  ('a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001');

INSERT INTO arbol (id_arbol, nombre_especifico, especie, latitud, longitud, fecha_registro, descripcion,
                   id_cuenta, id_dispositivo) VALUES
  ('b0000000-0000-0000-0000-000000000001', 'Oyamel del lago', 'Abies religiosa', 19.420500, -99.181800,
   '2026-10-01T15:00:00Z', 'Junto al lago menor',
   'a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001'),
  ('b0000000-0000-0000-0000-000000000002', 'Ahuehuete de la calzada', 'Taxodium mucronatum', 19.421300, -99.183100,
   '2026-10-05T17:30:00Z', NULL,
   'a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001'),
  -- Sin análisis todavía: su "último análisis" es null.
  ('b0000000-0000-0000-0000-000000000003', 'Pino del cerro', 'Pinus hartwegii', 19.419800, -99.184500,
   '2026-10-07T12:00:00Z', NULL,
   'a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001');

-- Zonas de follaje (la segunda sin radio ni descripción: columnas opcionales en TT_BKN).
INSERT INTO zona_follaje (id_zona, nombre_zona, latitud_centro, longitud_centro, radio_metros, fecha_registro,
                          descripcion, id_cuenta, id_dispositivo) VALUES
  ('c0000000-0000-0000-0000-000000000001', 'Zona lago menor', 19.421000, -99.182500, 50.0,
   '2026-10-02T16:00:00Z', 'Dosel mixto de oyameles',
   'a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001'),
  ('c0000000-0000-0000-0000-000000000002', 'Zona calzada', 19.422000, -99.184000, NULL,
   '2026-10-06T18:00:00Z', NULL,
   'a0000000-0000-0000-0000-000000000001', 'd0000000-0000-0000-0000-000000000001');

-- Análisis: corteza -> árbol, follaje -> zona (chk_arbol_o_zona).
-- ruta_evidencia en sus tres formas reales: clave (e1, e3, e4), URL de S3 (e2) y 'sin_imagen' (e5).
-- Orden por fecha_captura DESC: e5, e4, e2, e3, e1.
INSERT INTO analisis (id_analisis, ruta_evidencia, latitud_captura, longitud_captura, fecha_captura,
                      fecha_sincronizacion, estado_sincronizacion, tipo_imagen, resultado_clasificacion,
                      confianza, observaciones, id_arbol, id_zona) VALUES
  ('e0000000-0000-0000-0000-000000000001', 'evidencias/e1.jpg', 19.420500, -99.181800, '2026-10-03T15:10:00Z',
   '2026-10-03T15:20:00Z', 'sincronizado', 'corteza', 'infestado', 0.91, 'Galerías visibles',
   'b0000000-0000-0000-0000-000000000001', NULL),
  ('e0000000-0000-0000-0000-000000000002',
   'https://descortezadores-archivos-prod.s3.us-east-1.amazonaws.com/evidencias/e2.jpg', 19.421300, -99.183100, '2026-10-06T10:00:00Z',
   '2026-10-06T10:05:00Z', 'sincronizado', 'corteza', 'sano', 0.88, NULL,
   'b0000000-0000-0000-0000-000000000002', NULL),
  ('e0000000-0000-0000-0000-000000000003', 'evidencias/e3.jpg', 19.421000, -99.182500, '2026-10-04T12:00:00Z',
   '2026-10-04T12:30:00Z', 'sincronizado', 'follaje', 'sano', 0.95, NULL,
   NULL, 'c0000000-0000-0000-0000-000000000001'),
  ('e0000000-0000-0000-0000-000000000004', 'evidencias/e4.jpg', NULL, NULL, '2026-10-07T09:00:00Z',
   NULL, 'pendiente', 'follaje', 'no_determinado', 0.42, 'Poca luz',
   NULL, 'c0000000-0000-0000-0000-000000000001'),
  ('e0000000-0000-0000-0000-000000000005', 'sin_imagen', 19.422000, -99.184000, '2026-10-08T11:00:00Z',
   '2026-10-08T11:45:00Z', 'error', 'follaje', 'infestado', 0.77, NULL,
   NULL, 'c0000000-0000-0000-0000-000000000002');

-- Cromático solo para e3 y e4 (e5 es follaje sin cromático).
INSERT INTO analisis_cromatico (id_analisis_cromatico, pct_sano, pct_regular, pct_danado, pct_incierto, id_analisis) VALUES
  ('f0000000-0000-0000-0000-000000000003', 80.0, 10.0, 5.0, 5.0, 'e0000000-0000-0000-0000-000000000003'),
  ('f0000000-0000-0000-0000-000000000004', 30.0, 20.0, 10.0, 40.0, 'e0000000-0000-0000-0000-000000000004');
