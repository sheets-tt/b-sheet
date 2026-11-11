-- Réplica del esquema de producción (RDS descortezadores-db), que es propiedad de TT_BKN:
-- ../TT_BKN/docker/postgres/init-schema.sql, sin la extensión pgcrypto ni las cuentas semilla.
--
-- b-sheet NO migra producción (FLYWAY_ENABLED=false y usuario de solo lectura). Este archivo solo
-- construye la BD local y la de tests para que reflejen la real. Si TT_BKN cambia su esquema,
-- se refleja aquí con una migración nueva (V3__..., V4__...).

-- 1. CUENTA
CREATE TABLE IF NOT EXISTS cuenta (
    id_cuenta UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    correo VARCHAR(255) UNIQUE NOT NULL,
    contrasena_hash VARCHAR(255) NOT NULL,
    rol VARCHAR(50) NOT NULL CHECK (rol IN ('Administrador', 'Experto de campo')),
    estado_cuenta VARCHAR(50) NOT NULL DEFAULT 'Activa' CHECK (estado_cuenta IN ('Activa', 'desactivada')),
    fecha_creacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    nombre_completo VARCHAR(255) NOT NULL
);

-- 2. DISPOSITIVO
CREATE TABLE IF NOT EXISTS dispositivo (
    id_dispositivo UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    identificador_unico VARCHAR(255) UNIQUE NOT NULL,
    nombre_dispositivo VARCHAR(255),
    token_dispositivo VARCHAR(500),
    estado_dispositivo VARCHAR(50) NOT NULL DEFAULT 'Activo' CHECK (estado_dispositivo IN ('Activo', 'inactivo', 'bloqueado')),
    fecha_registro TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. CUENTA_DISPOSITIVO
CREATE TABLE IF NOT EXISTS cuenta_dispositivo (
    id_cuenta_dispositivo UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    id_cuenta UUID NOT NULL REFERENCES cuenta(id_cuenta) ON DELETE CASCADE,
    id_dispositivo UUID NOT NULL REFERENCES dispositivo(id_dispositivo) ON DELETE CASCADE,
    fecha_asociacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    fecha_desasociacion TIMESTAMP WITH TIME ZONE,
    estado_cuenta_dispositivo VARCHAR(50) NOT NULL DEFAULT 'Activa' CHECK (estado_cuenta_dispositivo IN ('Activa', 'desasociada'))
);

-- 4. ARBOL
CREATE TABLE IF NOT EXISTS arbol (
    id_arbol UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nombre_especifico VARCHAR(255) NOT NULL,
    especie VARCHAR(255) NOT NULL,
    longitud DOUBLE PRECISION,
    latitud DOUBLE PRECISION,
    fecha_registro TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    descripcion TEXT,
    id_cuenta UUID NOT NULL REFERENCES cuenta(id_cuenta),
    id_dispositivo UUID REFERENCES dispositivo(id_dispositivo)
);

-- 5. ZONA_FOLLAJE
CREATE TABLE IF NOT EXISTS zona_follaje (
    id_zona UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    latitud_centro DOUBLE PRECISION NOT NULL,
    longitud_centro DOUBLE PRECISION NOT NULL,
    fecha_registro TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    descripcion TEXT,
    nombre_zona VARCHAR(255) NOT NULL,
    radio_metros DOUBLE PRECISION,
    id_dispositivo UUID REFERENCES dispositivo(id_dispositivo),
    id_cuenta UUID NOT NULL REFERENCES cuenta(id_cuenta)
);

-- 6. ANALISIS (corteza -> árbol; follaje -> zona)
CREATE TABLE IF NOT EXISTS analisis (
    id_analisis UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ruta_evidencia VARCHAR(500) NOT NULL,
    latitud_captura DOUBLE PRECISION,
    longitud_captura DOUBLE PRECISION,
    fecha_captura TIMESTAMP WITH TIME ZONE NOT NULL,
    fecha_sincronizacion TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    estado_sincronizacion VARCHAR(50) NOT NULL DEFAULT 'sincronizado' CHECK (estado_sincronizacion IN ('pendiente', 'sincronizado', 'error')),
    tipo_imagen VARCHAR(50) NOT NULL CHECK (tipo_imagen IN ('corteza', 'follaje')),
    resultado_clasificacion VARCHAR(50) NOT NULL CHECK (resultado_clasificacion IN ('sano', 'infestado', 'no_determinado')),
    confianza DOUBLE PRECISION,
    observaciones TEXT,
    id_arbol UUID REFERENCES arbol(id_arbol) ON DELETE SET NULL,
    id_zona UUID REFERENCES zona_follaje(id_zona) ON DELETE SET NULL,
    CONSTRAINT chk_arbol_o_zona CHECK (
        (tipo_imagen = 'corteza' AND id_arbol IS NOT NULL AND id_zona IS NULL) OR
        (tipo_imagen = 'follaje' AND id_zona IS NOT NULL AND id_arbol IS NULL)
    )
);

-- 7. ANALISIS_CROMATICO
CREATE TABLE IF NOT EXISTS analisis_cromatico (
    id_analisis_cromatico UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pct_incierto DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    pct_danado DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    pct_regular DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    pct_sano DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    id_analisis UUID NOT NULL REFERENCES analisis(id_analisis) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_arbol_coords ON arbol(latitud, longitud);
CREATE INDEX IF NOT EXISTS idx_analisis_fecha ON analisis(fecha_captura DESC);
CREATE INDEX IF NOT EXISTS idx_analisis_arbol ON analisis(id_arbol);
CREATE INDEX IF NOT EXISTS idx_analisis_zona ON analisis(id_zona);
