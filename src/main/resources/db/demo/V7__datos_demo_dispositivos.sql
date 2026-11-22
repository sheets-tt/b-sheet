-- Datos de prueba: SOLO desarrollo y tests (application-dev.yml / application-test.yml).
-- Dispositivo de los análisis demo (e1..e4 y e6); e5 queda sin dispositivo para probar compatibilidad con NULL.
UPDATE analisis
SET id_dispositivo = 'd0000000-0000-0000-0000-000000000001'
WHERE id_analisis IN (
    'e0000000-0000-0000-0000-000000000001',
    'e0000000-0000-0000-0000-000000000002',
    'e0000000-0000-0000-0000-000000000003',
    'e0000000-0000-0000-0000-000000000004',
    'e0000000-0000-0000-0000-000000000006'
);
