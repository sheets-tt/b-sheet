-- Esquema inicial (ER "Sistema móvil y web"): cuentas, dispositivos, árboles, zonas de follaje
-- y análisis de imágenes con su análisis cromático.
-- Identificadores BIGINT IDENTITY; fechas TIMESTAMPTZ (UTC); coordenadas en grados decimales (WGS84).

-- ---------------------------------------------------------------------------------------------
-- CUENTA: usuario del sistema (login web y móvil).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cuenta (
    id_cuenta        BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    correo           VARCHAR(255) NOT NULL,
    contrasena_hash  VARCHAR(100) NOT NULL,                       -- bcrypt
    rol              VARCHAR(20)  NOT NULL DEFAULT 'USUARIO',
    estado_cuenta    VARCHAR(20)  NOT NULL DEFAULT 'ACTIVA',
    fecha_creacion   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    nombre_completo  VARCHAR(255) NOT NULL,
    CONSTRAINT ck_cuenta_rol    CHECK (rol IN ('ADMIN', 'USUARIO')),
    CONSTRAINT ck_cuenta_estado CHECK (estado_cuenta IN ('ACTIVA', 'INACTIVA', 'BLOQUEADA'))
);
-- Login por correo sin distinguir mayúsculas.
CREATE UNIQUE INDEX ux_cuenta_correo_lower ON cuenta (lower(correo));

-- ---------------------------------------------------------------------------------------------
-- DISPOSITIVO: teléfono o equipo que captura datos.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE dispositivo (
    id_dispositivo       BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    identificador_unico  VARCHAR(255) NOT NULL,
    nombre_dispositivo   VARCHAR(255) NOT NULL,
    token_dispositivo    VARCHAR(512),                             -- p.ej. token de notificaciones
    estado_dispositivo   VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    fecha_registro       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_dispositivo_identificador UNIQUE (identificador_unico),
    CONSTRAINT ck_dispositivo_estado CHECK (estado_dispositivo IN ('ACTIVO', 'INACTIVO', 'REVOCADO'))
);

-- ---------------------------------------------------------------------------------------------
-- CUENTA_DISPOSITIVO: historial de asociaciones cuenta <-> dispositivo.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE cuenta_dispositivo (
    id_cuenta_dispositivo      BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id_cuenta                  BIGINT      NOT NULL REFERENCES cuenta (id_cuenta),
    id_dispositivo             BIGINT      NOT NULL REFERENCES dispositivo (id_dispositivo),
    fecha_asociacion           TIMESTAMPTZ NOT NULL DEFAULT now(),
    fecha_desasociacion        TIMESTAMPTZ,                          -- NULL mientras siga asociada
    estado_cuenta_dispositivo  VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT ck_cuenta_dispositivo_estado CHECK (estado_cuenta_dispositivo IN ('ACTIVA', 'INACTIVA')),
    CONSTRAINT ck_cuenta_dispositivo_fechas
        CHECK (fecha_desasociacion IS NULL OR fecha_desasociacion >= fecha_asociacion)
);
CREATE INDEX ix_cuenta_dispositivo_cuenta      ON cuenta_dispositivo (id_cuenta);
CREATE INDEX ix_cuenta_dispositivo_dispositivo ON cuenta_dispositivo (id_dispositivo);
-- Una misma pareja cuenta-dispositivo solo puede tener una asociación vigente.
CREATE UNIQUE INDEX ux_cuenta_dispositivo_vigente
    ON cuenta_dispositivo (id_cuenta, id_dispositivo) WHERE fecha_desasociacion IS NULL;

-- ---------------------------------------------------------------------------------------------
-- ARBOL: árbol registrado en campo.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE arbol (
    id_arbol           BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_especifico  VARCHAR(255),
    especie            VARCHAR(255),
    latitud            NUMERIC(9, 6) NOT NULL,
    longitud           NUMERIC(9, 6) NOT NULL,
    fecha_registro     TIMESTAMPTZ   NOT NULL DEFAULT now(),
    descripcion        TEXT,
    id_cuenta          BIGINT        NOT NULL REFERENCES cuenta (id_cuenta),
    id_dispositivo     BIGINT        NOT NULL REFERENCES dispositivo (id_dispositivo),
    CONSTRAINT ck_arbol_latitud  CHECK (latitud  BETWEEN -90  AND 90),
    CONSTRAINT ck_arbol_longitud CHECK (longitud BETWEEN -180 AND 180)
);
CREATE INDEX ix_arbol_cuenta      ON arbol (id_cuenta);
CREATE INDEX ix_arbol_dispositivo ON arbol (id_dispositivo);

-- ---------------------------------------------------------------------------------------------
-- ZONA_FOLLAJE: zona circular (centro + radio) de follaje.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE zona_follaje (
    id_zona          BIGINT         GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nombre_zona      VARCHAR(255)   NOT NULL,
    latitud_centro   NUMERIC(9, 6)  NOT NULL,
    longitud_centro  NUMERIC(9, 6)  NOT NULL,
    radio_metros     NUMERIC(10, 2) NOT NULL,
    fecha_registro   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    descripcion      TEXT,
    id_dispositivo   BIGINT         NOT NULL REFERENCES dispositivo (id_dispositivo),
    id_cuenta        BIGINT         NOT NULL REFERENCES cuenta (id_cuenta),
    CONSTRAINT ck_zona_latitud  CHECK (latitud_centro  BETWEEN -90  AND 90),
    CONSTRAINT ck_zona_longitud CHECK (longitud_centro BETWEEN -180 AND 180),
    CONSTRAINT ck_zona_radio    CHECK (radio_metros > 0)
);
CREATE INDEX ix_zona_follaje_cuenta      ON zona_follaje (id_cuenta);
CREATE INDEX ix_zona_follaje_dispositivo ON zona_follaje (id_dispositivo);

-- ---------------------------------------------------------------------------------------------
-- ANALISIS: imagen capturada y su clasificación. Pertenece a una zona y, opcionalmente, a un árbol.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE analisis (
    id_analisis              BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ruta_evidencia           VARCHAR(1024) NOT NULL,              -- p.ej. clave del objeto en S3
    tipo_imagen              VARCHAR(50),
    latitud_captura          NUMERIC(9, 6),
    longitud_captura         NUMERIC(9, 6),
    fecha_captura            TIMESTAMPTZ   NOT NULL,
    fecha_sincronizacion     TIMESTAMPTZ,                          -- NULL hasta que llega al servidor
    estado_sincronizacion    VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    resultado_clasificacion  VARCHAR(50),
    confianza                NUMERIC(5, 4),                        -- 0.0000 a 1.0000
    observaciones            TEXT,
    id_arbol                 BIGINT        REFERENCES arbol (id_arbol),  -- opcional (O)
    id_zona                  BIGINT        NOT NULL REFERENCES zona_follaje (id_zona),
    CONSTRAINT ck_analisis_estado_sinc CHECK (estado_sincronizacion IN ('PENDIENTE', 'SINCRONIZADO', 'ERROR')),
    CONSTRAINT ck_analisis_confianza   CHECK (confianza IS NULL OR confianza BETWEEN 0 AND 1),
    CONSTRAINT ck_analisis_latitud     CHECK (latitud_captura  IS NULL OR latitud_captura  BETWEEN -90  AND 90),
    CONSTRAINT ck_analisis_longitud    CHECK (longitud_captura IS NULL OR longitud_captura BETWEEN -180 AND 180)
);
CREATE INDEX ix_analisis_arbol ON analisis (id_arbol);
CREATE INDEX ix_analisis_zona  ON analisis (id_zona);

-- ---------------------------------------------------------------------------------------------
-- ANALISIS_CROMATICO: porcentajes de color del análisis (0-100).
-- ---------------------------------------------------------------------------------------------
CREATE TABLE analisis_cromatico (
    id_analisis_cromatico  BIGINT        GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    pct_sano               NUMERIC(5, 2) NOT NULL,
    pct_regular            NUMERIC(5, 2) NOT NULL,
    pct_danado             NUMERIC(5, 2) NOT NULL,
    pct_incierto           NUMERIC(5, 2) NOT NULL,
    id_analisis            BIGINT        NOT NULL REFERENCES analisis (id_analisis),
    CONSTRAINT ck_cromatico_sano      CHECK (pct_sano      BETWEEN 0 AND 100),
    CONSTRAINT ck_cromatico_regular   CHECK (pct_regular   BETWEEN 0 AND 100),
    CONSTRAINT ck_cromatico_danado    CHECK (pct_danado    BETWEEN 0 AND 100),
    CONSTRAINT ck_cromatico_incierto  CHECK (pct_incierto  BETWEEN 0 AND 100)
);
CREATE INDEX ix_analisis_cromatico_analisis ON analisis_cromatico (id_analisis);
