# b-sheet — backend web de sheets-tt

Backend de **sheets-tt** para la web: Micronaut + PostgreSQL, empaquetado para
**AWS Lambda (imagen de contenedor `java25`, arm64)**, detrás de **API Gateway HTTP API
(payload v2)**. Lee la base de datos de **TT_BKN** (RDS `descortezadores-db`, donde escribe la app
móvil). La única escritura es la **gestión de cuentas** por un administrador (tabla `cuenta`, columnas
concretas: ver [Permisos en producción](#permisos-en-producción)); b-sheet no toca las demás tablas ni migra
producción.

Endpoints: `GET /health`, `POST /auth/login`, `GET /me` (contrato `API_CONTRACT.md` v1), los módulos
de consulta, todos `GET` y con JWT (ver [Módulos de consulta](#módulos-de-consulta)), y la
[gestión de cuentas](#gestión-de-cuentas) (`POST`/`PUT`/`PATCH /cuentas`, solo `ADMINISTRADOR`).

## Arquitectura

```
Web (S3 + CloudFront) ──/api/*──► CloudFront ──► API Gateway HTTP API ($default, payload 2.0)
                                                          │  ANY /{proxy+}
                                                          ▼
                                    Lambda java25 arm64, imagen de contenedor (alias "live")
                                    handler: io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction
                                                          │  JDBC + SSL (Hikari, pool máx. 2), usuario sheets_lectura
                                                          │  (SELECT + INSERT/UPDATE de columnas de cuenta)
                                                          ▼
                                    RDS PostgreSQL descortezadores-db (esquema de TT_BKN)
```

| Pieza | Detalle |
|---|---|
| Framework | Micronaut 5 (`micronaut-parent`, `micronaut.runtime=lambda`) |
| Build | Maven (wrapper `./mvnw`), Java 25, jar "fat" con `maven-shade-plugin` |
| Persistencia | Micronaut Data JDBC (`GenericRepository`, sin escrituras) y SQL con `JdbcOperations` (lecturas y la escritura de `cuenta`) + HikariCP. Un solo datasource. Flyway solo en dev y tests |
| Seguridad | Micronaut Security JWT, HS256 (`JWT_SECRET`), contraseñas bcrypt (jBCrypt) |
| Arranque | Sin SnapStart (Lambda no lo admite con imágenes de contenedor). `micronaut-crac` queda listo por si se pasa a un ZIP con el runtime `java25` |

### Capas (TDD)

Cada módulo sigue la misma estructura, y cada capa tiene su test:

| Capa | Ejemplo (`tt.sheets.arbol`) | Test |
|---|---|---|
| `domain` — modelo y puertos, sin Micronaut | `Arbol`, `ArbolRepository` | — |
| `application` — casos de uso, sin Micronaut | `ConsultarArboles` | `ConsultarArbolesTest` (dobles en memoria) |
| `infrastructure` — adaptadores y cableado | `JdbcArbolRepository`, `ArbolEntity`, `ArbolBeans` (`@Factory`) | `JdbcArbolRepositoryTest` (BD de tests) |
| Controlador + DTO | `ArbolController`, `ArbolResponse` | `ArbolesApiTest` (handler Lambda real) |

Adaptadores: las tablas simples usan repositorios de Micronaut Data (`GenericRepository`, sin escrituras);
los filtros opcionales, JOINs, agregados y las escrituras de `cuenta` usan SQL con `JdbcOperations`, siempre
con parámetros enlazados y nombrando columnas, también en `INSERT/UPDATE ... RETURNING` (así `contrasena_hash`
y `token_dispositivo` nunca salen de la BD).

## Módulos de consulta

Todos requieren `Authorization: Bearer <jwt>` (401 sin token). Fechas en ISO-8601 UTC; las claves nulas
se envían como `null`; un id que no es UUID da 400 y uno que no existe, 404.

| Endpoint | Rol | Respuesta |
|---|---|---|
| `GET /arboles`, `GET /arboles/{id}` | cualquiera | `{id, nombreEspecifico, especie, latitud, longitud, fechaRegistro, descripcion, ultimoAnalisis}`, del más reciente al más antiguo. `ultimoAnalisis` = `{id, resultado, fechaCaptura}` o `null` |
| `GET /zonas`, `GET /zonas/{id}` | cualquiera | `{id, nombreZona, latitudCentro, longitudCentro, radioMetros, fechaRegistro, descripcion, ultimoAnalisis}` |
| `GET /analisis`, `GET /analisis/{id}` | cualquiera | `{id, tieneEvidencia, latitudCaptura, longitudCaptura, fechaCaptura, fechaSincronizacion, estadoSincronizacion, tipoImagen, resultadoClasificacion, confianza, observaciones, idArbol, idZona, cromatico}`; `cromatico` = `{pctSano, pctRegular, pctDanado, pctIncierto}` o `null` |
| `GET /analisis/{id}/evidencia` | cualquiera | `{url, expira}`: URL de S3 firmada por 5 min (`Cache-Control: no-store`); 404 si no tiene foto |
| `GET /estadisticas` | cualquiera | `totales` (árboles, zonas, análisis, dispositivos, árboles y zonas con algún análisis `infestado`), conteos por resultado, tipo y sincronización (todas las categorías, 0 si no hay, en orden fijo), `cromaticoPromedio` (2 decimales) y `ultimaCaptura` |
| `GET /cuentas` | `ADMINISTRADOR` (403 si no) | `{id, correo, nombreCompleto, rol, activa, fechaCreacion}`, por correo. Sin hash |
| `POST /cuentas`, `PUT /cuentas/{id}`, `PATCH /cuentas/{id}/desactivar`, `PATCH /cuentas/{id}/activar` | `ADMINISTRADOR` (403 si no) | La cuenta como en `GET /cuentas`. Ver [Gestión de cuentas](#gestión-de-cuentas) |
| `GET /dispositivos` | `ADMINISTRADOR` (403 si no) | `{id, identificadorUnico, nombreDispositivo, estado, fechaRegistro, cuentaVigente}`. Sin token |

Fotos de evidencia: el bucket de TT_BKN (`descortezadores-archivos-prod`) es privado y también guarda
despliegues. `ClaveEvidencia` solo acepta claves de ese bucket bajo `evidencias/` (URL completa de S3 o
clave relativa, sin `.`/`..`); `sin_imagen` o cualquier otra ruta dan 404. La URL se firma localmente
(SigV4, `S3Presigner`, sin clientes HTTP) con el rol de la Lambda, que en `template.yaml` solo tiene
`s3:GetObject` sobre `{bucket}/evidencias/*`. Variables: `EVIDENCIAS_BUCKET`, `EVIDENCIAS_REGION`.
La ruta interna (`ruta_evidencia`) no se expone: la web solo recibe `tieneEvidencia`.

Filtros de `GET /analisis` (todos opcionales y combinables): `tipo` (`corteza`/`follaje`), `resultado`
(`sano`/`infestado`/`no_determinado`), `estado` (`pendiente`/`sincronizado`/`error`), `desde` y `hasta`
(instantes ISO-8601, inclusivos sobre `fechaCaptura`), `arbol` y `zona` (UUID), `limite` (1–500, 100 por
defecto). Orden: captura más reciente primero. Un valor inválido o `desde` > `hasta` da 400 con
`{"message": "..."}`. Ejemplo: `GET /analisis?tipo=follaje&resultado=infestado&desde=2026-10-01T00:00:00Z`.

## Gestión de cuentas

No hay registro público (RN-01): solo un `ADMINISTRADOR` crea cuentas, y las edita, desactiva y reactiva
desde la web (RF-26, CU-A02). Las cuentas no se borran. Todas las respuestas son la cuenta como en
`GET /cuentas` (`{id, correo, nombreCompleto, rol, activa, fechaCreacion}`, sin hash) y los errores,
`{"message": "..."}`.

| Endpoint | Cuerpo | Éxito |
|---|---|---|
| `POST /cuentas` | `{correo, nombreCompleto, rol, contrasena}` | 201. Nace activa, con `fechaCreacion` = ahora |
| `PUT /cuentas/{id}` | `{correo, nombreCompleto, rol, contrasena?}`: `contrasena` ausente, `null` o en blanco conserva la actual | 200 |
| `PATCH /cuentas/{id}/desactivar` | — | 200 (`estado_cuenta = 'desactivada'`; repetirlo no falla) |
| `PATCH /cuentas/{id}/activar` | — | 200 (`estado_cuenta = 'Activa'`) |

Reglas:

- `correo`: correo válido de hasta 254 caracteres; se guarda sin espacios y en minúsculas. Único sin importar
  mayúsculas (el `UNIQUE` de TT_BKN sí las distingue, por eso se revisa con `lower(correo)`).
- `nombreCompleto`: 1–255 caracteres, sin espacios alrededor. `rol`: `ADMINISTRADOR` o `EXPERTO_CAMPO`
  (en la BD, `Administrador` / `Experto de campo`).
- `contrasena`: obligatoria al crear; al menos 8 caracteres, no en blanco y **máximo 72 bytes en UTF-8**
  (lo que BCrypt usa; el login sigue aceptando hasta 256 para las cuentas que creó TT_BKN). Se guarda con
  BCrypt cost 10 (`$2a$`, como TT_BKN), así que la cuenta también entra a la app móvil.
- Los campos que no son del contrato (estado, hash, id, fecha) se ignoran.

| Estado | Cuándo |
|---|---|
| 400 | Cualquier regla anterior, JSON mal formado o `id` que no es UUID |
| 401 / 403 | Sin token / el token no es de `ADMINISTRADOR` |
| 404 | El `id` no existe |
| 409 | El correo ya es de otra cuenta (`Ya existe una cuenta con ese correo`); el administrador intenta desactivarse o quitarse el rol `ADMINISTRADOR` (se quedaría sin acceso); o el cambio dejaría el sistema sin administradores activos (`Debe quedar al menos un administrador activo`: p. ej., dos administradores que se desactivan uno al otro a la vez; el `UPDATE` bloquea las filas de los administradores activos y solo aplica el cambio si queda otro) |

**Efecto inmediato en los tokens**: el login guarda en el JWT el id de la cuenta (claim `cid`) además del
correo (`sub`) y el rol. Un JWT solo autentica si la cuenta de ese id sigue activa, con el mismo correo y el
mismo rol que dice el token (`CuentaVigenteJwtAuthenticationFactory`, una consulta por petición autenticada).
Así, un token viejo nunca vale por otra cuenta que después tome ese correo. Al desactivar una cuenta o
cambiarle el rol o el correo, sus tokens ya emitidos dan 401 en todos los endpoints, sin esperar a que
venzan; se vuelve a iniciar sesión para obtener uno con el rol nuevo. Los tokens sin `cid` (emitidos antes
de este cambio) dan 401: después de desplegarlo, todos vuelven a iniciar sesión una vez. Si un administrador cambia su propio
correo, su token deja de valer y tiene que volver a entrar con el correo nuevo. Un token viejo enviado al login
no estorba. Reactivar una cuenta vuelve a aceptar los tokens suyos que no hayan vencido.

Código:

- `tt.sheets.HealthController` — `GET /health` → `{"status":"UP"}` (no toca la BD).
- `tt.sheets.auth.AuthController` — `POST /auth/login`: busca la cuenta por correo
  (case-insensitive, solo `estado_cuenta = 'Activa'`), valida bcrypt (tiempo constante aunque la
  cuenta no exista) y emite el JWT con el rol en `roles`: `ADMINISTRADOR` o `EXPERTO_CAMPO`
  (en la BD: `Administrador` / `Experto de campo`, ver `tt.sheets.cuenta.Rol`).
  200 con `access_token`, `token_type`, `expires_in`, `username`, `roles`; 401 `{"message": "..."}`;
  400 si faltan campos o están vacíos.
- `tt.sheets.auth.MeController` — `GET /me` (Bearer) → `{"username": correo, "displayName":
  nombre_completo}`; 401 sin token, con token inválido o expirado, o si la cuenta ya no está activa.
- `tt.sheets.cuenta` — entidad `Cuenta` (tabla `cuenta`), `Rol` y repositorio.
- `tt.sheets.administracion.GestionCuentasController` — alta y cambios de cuentas; reglas en
  `GestionarCuentas` y `Contrasenas`, SQL en `JdbcAdministracionRepository`.

## Base de datos

La base de producción es de **TT_BKN** (`../TT_BKN/docker/postgres/init-schema.sql`). b-sheet guarda una
**réplica** de ese esquema en `db/migration/V1__esquema_ecoverde.sql` para construir la base local y la
de tests; **Flyway está apagado por defecto** (`FLYWAY_ENABLED=false`) y solo se enciende en
`application-dev.yml` y `application-test.yml`. Si TT_BKN cambia su esquema, se refleja aquí con una
migración nueva.

| Tabla | Detalle |
|---|---|
| `cuenta` | Id `UUID`, `correo` único, `contrasena_hash` (bcrypt), `rol` (`Administrador`/`Experto de campo`), `estado_cuenta` (`Activa`/`desactivada`) |
| `dispositivo` | `identificador_unico` único, `estado_dispositivo` (`Activo`/`inactivo`/`bloqueado`) |
| `cuenta_dispositivo` | → `cuenta`, → `dispositivo`. `estado_cuenta_dispositivo` (`Activa`/`desasociada`) |
| `arbol` | → `cuenta`, → `dispositivo` (opcional). Coordenadas `DOUBLE PRECISION` |
| `zona_follaje` | → `cuenta`, → `dispositivo` (opcional). Centro + `radio_metros` |
| `analisis` | `tipo_imagen` `corteza` → `arbol` o `follaje` → `zona_follaje` (exactamente uno). `resultado_clasificacion` (`sano`/`infestado`/`no_determinado`), `estado_sincronizacion` (`pendiente`/`sincronizado`/`error`) |
| `analisis_cromatico` | → `analisis`. `pct_sano`, `pct_regular`, `pct_danado`, `pct_incierto` |

| Carpeta | Contenido | Dónde se aplica |
|---|---|---|
| `db/migration` | Réplica del esquema de TT_BKN (`V1`, `V3`, …) | Solo desarrollo y tests (nunca producción) |
| `db/demo` | Datos de prueba (`V2`): cuentas `demo@sheets.tt` (Experto de campo), `admin@sheets.tt` (Administrador) y `baja@sheets.tt` (desactivada), todas con `Demo1234!`; un dispositivo, dos árboles, dos zonas, cinco análisis (`e1`–`e5`) y dos cromáticos | Solo desarrollo y tests |

`db/migration` + `db/demo` son la **réplica local** del esquema de producción: las pruebas de gestión de
cuentas escriben en ella (y borran lo que crean), pero no cambian el esquema. Producción no se migra.

### Permisos en producción

En producción b-sheet entra con el usuario de `DB_USER` (`sheets_lectura`), que hoy solo tiene `SELECT`. Para
la gestión de cuentas necesita, **solo sobre `cuenta`** y por columna (sin `DELETE` ni DDL), lo que debe
ejecutar el dueño de la BD de TT_BKN (b-sheet no lo hace):

```sql
GRANT INSERT (id_cuenta, correo, contrasena_hash, rol, estado_cuenta, fecha_creacion, nombre_completo)
  ON cuenta TO sheets_lectura;
GRANT UPDATE (correo, contrasena_hash, rol, estado_cuenta, nombre_completo)
  ON cuenta TO sheets_lectura;

-- Comprobar:
SELECT privilege_type, column_name FROM information_schema.column_privileges
WHERE grantee = 'sheets_lectura' AND table_name = 'cuenta' ORDER BY 1, 2;
-- Si el rol tiene transacciones de solo lectura por defecto, quitarlo:
SELECT rolconfig FROM pg_roles WHERE rolname = 'sheets_lectura';   -- ¿default_transaction_read_only=on?
ALTER ROLE sheets_lectura RESET default_transaction_read_only;      -- solo si aparece
```

El `id_cuenta` se genera en Java (no depende de `gen_random_uuid()` ni de pgcrypto) y `fecha_creacion` es
`now()`. El `SELECT` que ya tiene cubre el `WHERE` y el `RETURNING`, y el `UPDATE` por columna basta para el `SELECT ... FOR UPDATE` que protege al último administrador. Sin estos permisos los `GET` siguen
funcionando y las escrituras dan 500 (`permission denied for table cuenta`). Comprobado contra la réplica local
con un rol que solo tenía `SELECT` y estos `GRANT`.

`ProductionMigrationsTest` falla si alguna migración de `db/migration` inserta cuentas, y
`ProductionConfigTest` falla si Flyway queda encendido sin los entornos `dev` o `test`.

Las migraciones se aplican de dos formas, y las dos usan la misma tabla `flyway_schema_history`
(Flyway 12.11.0 en ambos casos):

1. **Flyway en Docker** (`docker-compose.yml`), para construir o actualizar una base sin arrancar
   el backend:
   ```bash
   # Local: Postgres 18 en localhost:5434 (usuario/clave sheets/sheets), con db/migration + db/demo
   docker compose up -d db
   docker compose run --rm flyway            # migrate + info
   docker compose run --rm flyway validate
   docker compose down                       # -v borra también los datos del volumen

   # Neon (producción): solo db/migration; las credenciales salen de secrets/neon.enc.env (SOPS)
   sops exec-env secrets/neon.enc.env 'docker compose run --rm flyway-neon info'
   sops exec-env secrets/neon.enc.env 'docker compose run --rm flyway-neon migrate'
   ```
   El servicio `flyway` siempre apunta al `db` local, aunque haya variables de Neon en el entorno,
   así que la cuenta demo no puede llegar a producción por error.
   Para usar la base local con el backend: `DB_URL=jdbc:postgresql://localhost:5434/sheets
   DB_USER=sheets DB_PASSWORD=sheets ./mvnw mn:run -Pdev`.
2. **Al arrancar el backend en dev o tests** (Flyway encendido en esos entornos): Micronaut aplica lo que falte.
   Si la base ya está al día (por ejemplo, migrada con Docker), no hace nada.

Para comprobar que Neon tiene todas las migraciones de `db/migration` (se salta sin credenciales):
```bash
sops exec-env secrets/neon.enc.env './mvnw test -Dtest=NeonDatabaseTest'
```

### Secretos de Neon (SOPS + age)

`secrets/neon.enc.env` guarda `FLYWAY_URL`, `FLYWAY_USER` y `FLYWAY_PASSWORD` cifrados con SOPS para
la llave pública de `.sops.yaml`. Para descifrarlo hace falta la llave privada (con passphrase) y
`SOPS_AGE_SSH_PRIVATE_KEY_FILE` apuntando a ella. Usa la conexión **directa** de Neon (el host sin
`-pooler`), porque Flyway necesita bloqueos que el pooler no soporta.

```bash
sops edit secrets/neon.enc.env                                  # editar (se vuelve a cifrar al guardar)
sops set secrets/neon.enc.env '["FLYWAY_URL"]' '"jdbc:..."'     # cambiar un solo valor
```

Reglas: una migración aplicada **no se edita**; cualquier cambio va en un archivo nuevo
`V3__descripcion.sql`, `V4__...`. Flyway rechaza arrancar si el checksum de una migración aplicada
cambia. El número `V2` ya lo usa `db/demo`: no lo repitas en `db/migration`.

## Variables de entorno

| Variable | Obligatoria | Default | Descripción |
|---|---|---|---|
| `DB_URL` | sí (prod) | `jdbc:postgresql://localhost:5432/sheets` | JDBC URL. En producción usar SSL: `?sslmode=require` o `?sslmode=verify-full&sslrootcert=...` |
| `DB_USER` | sí (prod) | `postgres` (en `run` y tests: tu usuario del SO) | Usuario de la BD |
| `SECRETOS_ID` | **sí (AWS)** | vacío | Nombre del secreto de Secrets Manager con `{"DB_PASSWORD", "JWT_SECRET"}`. Lo lee `LambdaHandler` al arrancar; solo acepta esas dos claves. En AWS **no** se usan `DB_PASSWORD` ni `JWT_SECRET` como variables (se leían con `lambda:GetFunctionConfiguration`) |
| `DB_PASSWORD` | solo local | vacío | Contraseña de la BD (en AWS sale del secreto) |
| `JWT_SECRET` | **sí** (local; en AWS sale del secreto) | solo hay default en entorno `dev` y `test` | Secreto HS256, **≥ 32 caracteres**. Sin él, la app no arranca (fail-fast) |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Orígenes permitidos, separados por comas |
| `CONTEXT_PATH` | no | vacío | Prefijo de rutas. Vacío en el despliegue estándar (ver «Rutas `/api`»); `/api` solo sin la CloudFront Function |
| `FLYWAY_ENABLED` | no | `false` (`true` en dev y tests) | Ejecutar migraciones al arrancar. **No** encender en producción: la BD es de TT_BKN |
| `PORT` | no | `8080` | Puerto local (solo `./mvnw mn:run -Pdev`) |
| `LOG_LEVEL` | no | `INFO` | Nivel de log raíz |
| `DB_TEST_URL` | no | `jdbc:postgresql://localhost:5432/sheets_test` | BD que usan los tests |

Nunca se commitean secretos: `.env` y `*.local.env` están en `.gitignore`.

## Desarrollo local

Requisitos: **JDK 25** y PostgreSQL local. No hace falta instalar Maven: `./mvnw` descarga la
versión fijada (3.9.11). Apunta `JAVA_HOME` al JDK 25:

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@25   # o ~/Library/Java/JavaVirtualMachines/openjdk-25.0.1/Contents/Home
```

```bash
createdb sheets          # BD de desarrollo
createdb sheets_test     # BD de tests

./mvnw mn:run -Pdev      # entorno "dev": JWT_SECRET de desarrollo, DB_USER = tu usuario del SO
# otro puerto:  PORT=18080 ./mvnw mn:run -Pdev
```

Prueba rápida:

```bash
curl -s localhost:8080/health
# {"status":"UP"}

TOKEN=$(curl -s -X POST localhost:8080/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"demo@sheets.tt","password":"Demo1234!"}' | sed -E 's/.*"access_token":"([^"]+)".*/\1/')

curl -s localhost:8080/me -H "Authorization: Bearer $TOKEN"
# {"username":"demo@sheets.tt","displayName":"Usuario Demo"}
```

> El perfil `dev` agrega Netty como servidor HTTP solo para el arranque local; no entra al jar
> de Lambda. Ese perfil también activa el entorno `dev` de Micronaut y usa `DB_USER` (o tu usuario
> del SO) como rol de Postgres.

### Tests

```bash
./mvnw test
```

Los tests (JUnit 5) **invocan el handler real de Lambda** (`APIGatewayV2HTTPEventFunction`)
con eventos `APIGatewayV2HTTPEvent` (payload 2.0), con el mismo builder de contexto que usa
AWS, contra la BD `sheets_test`. Cubren health, login OK (payload exacto del contrato), login
case-insensitive, 401, 400, `/me` con y sin token, token inválido, CORS, `CONTEXT_PATH=/api` y la gestión
de cuentas (`GestionCuentasApiTest`: permisos, validación, 404/409, contraseñas y tokens cortados al
desactivar o cambiar el rol).

**Entradas que se rechazan** (las pruebas lo fijan):

| Entrada | Respuesta | Prueba |
|---|---|---|
| JSON mal formado o de otro tipo en el login | 400, sin detalles internos | `AuthApiTest` |
| Correo de más de 254 caracteres o contraseña de más de 256 | 400 | `AuthApiTest` |
| Token vencido, firmado con otra clave, `alg: none`, con el rol cambiado, o basura | 401 | `TokenManipuladoApiTest` |
| Filtros con valores inválidos, rango de fechas invertido, `limite` fuera de 1–500 o no numérico | 400 | `AnalisisApiTest` |
| Intentos de inyección SQL en filtros e identificadores | 400, sin tocar datos | `AnalisisApiTest` |
| Parámetro repetido (`?tipo=corteza&tipo=follaje`) | 400 (`ParametrosRepetidosFilter`, en todos los endpoints) | `AnalisisApiTest` |
| Alta o edición de cuenta con correo inválido, nombre vacío, rol desconocido o contraseña corta, en blanco o de más de 72 bytes | 400 | `GestionCuentasApiTest` |
| Correo de otra cuenta (también con otras mayúsculas) | 409 | `GestionCuentasApiTest` |
| Administrador que se desactiva o se quita el rol | 409 | `GestionCuentasApiTest` |
| Desactivar o degradar al último administrador activo, también con dos peticiones a la vez | 409 | `JdbcGestionCuentasRepositoryTest` |
| Token de una cuenta desactivada o con el rol cambiado | 401 | `GestionCuentasApiTest` |
| Token viejo cuando su correo pasa a otra cuenta; token sin `cid`, con el `cid` de otra cuenta o que no es UUID | 401 | `GestionCuentasApiTest`, `TokenManipuladoApiTest` |

Se aceptan a propósito: parámetros desconocidos (se ignoran), parámetros vacíos (cuentan como no
enviados) y contraseñas de más de 72 bytes (BCrypt solo usa los primeros 72, pero TT_BKN no limita
el largo al registrar y su dueño tiene que poder entrar).

## Empaquetado para Lambda

```bash
./mvnw package           # tests + jar
# → target/sheets-backend-lambda.jar

docker build -t sheets-backend .   # imagen de Lambda (Dockerfile, base public.ecr.aws/lambda/java:25)
```

- Runtime: imagen de contenedor `public.ecr.aws/lambda/java:25` (arquitectura `arm64`)
- Bytecode: Java 25 (class file 69)
- Handler: `io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction`

## Despliegue con AWS SAM

`template.yaml` define la función (imagen de contenedor java25 en arm64/Graviton, 1024 MB, timeout 20 s, alias `live`
sobre la última versión publicada), el HTTP API (`ANY /{proxy+}`, stage `$default`,
payload 2.0) y un log group con retención de 14 días.

En CI/CD lo hace el job `deploy` de `.github/workflows/pipeline.yml`. A mano, con una imagen ya
publicada en ECR:

```bash
sam validate
sam deploy --guided \
  --stack-name sheets-backend \
  --capabilities CAPABILITY_IAM \
  --image-repository <cuenta>.dkr.ecr.<región>.amazonaws.com/sheets-tt/b-sheet \
  --parameter-overrides \
    ImageUri='<cuenta>.dkr.ecr.<región>.amazonaws.com/sheets-tt/b-sheet@sha256:...' \
    DbUrl='jdbc:postgresql://db.example.com:5432/sheets?sslmode=require' \
    DbUser=sheets DbPassword='***' JwtSecret='***(>=32)***' \
    CorsAllowedOrigins='https://app.example.com'   # ContextPath vacío (default)
```

No hace falta `sam build`: la función usa la imagen indicada en `ImageUri`. La salida
`ApiDomain` es el origen que CloudFront debe usar para `/api/*`.

> **No pases `ContextPath='/api'`** si el API va detrás de la distribución de f-sheet:
> su CloudFront Function ya quita `/api`, y con las dos cosas el backend esperaría `/api/me`
> pero recibiría `/me` (404). `ContextPath=/api` solo se usa si alguien apunta CloudFront
> (u otro proxy) al API **sin** esa función.

Notas:

- **Secretos**: `DbPassword` y `JwtSecret` son parámetros `NoEcho`, pero quedan como variables
  de entorno de la función. Para producción conviene moverlos a SSM Parameter Store
  o Secrets Manager.
- **Rutas `/api`**: la distribución del frontend (`f-sheet`, `infra/cloudfront.yaml`)
  incluye la CloudFront Function `StripApiPrefixFunction`, que convierte `/api/me` en `/me`
  antes de enviar la petición a API Gateway. Por eso, en el despliegue estándar,
  **`ContextPath` se deja vacío** (es el default del template) y el backend expone `/health`,
  `/auth/login` y `/me` tal como dice el contrato. Los clientes que llaman directo a
  API Gateway (móvil) usan esas mismas rutas sin prefijo.
  `ContextPath=/api` **solo aplica** si CloudFront reenvía `/api/*` sin esa función
  (o si no se usa la distribución de f-sheet); en ese caso el backend serviría `/api/health`, etc.
- **CORS** lo resuelve Micronaut. No configures CORS en el HTTP API, porque API Gateway
  respondería los preflight con otras reglas.
- **Sin SnapStart**: Lambda no lo admite en funciones de imagen de contenedor. Si el arranque en
  frío llega a molestar, la salida es empaquetar como ZIP con el runtime `java25` y activar
  SnapStart (`micronaut-crac` ya suspende Hikari antes del snapshot). Flyway corre durante la
  inicialización: en producción va apagado (`FlywayEnabled=false`, el usuario no tiene DDL).
- **Arranque en frío**: `JAVA_TOOL_OPTIONS=-XX:+TieredCompilation -XX:TieredStopAtLevel=1`,
  logs concisos (CloudWatch ya agrega fecha y request id), Hikari y Flyway en `WARN`.

## Red: Lambda → PostgreSQL externo

- **SSL obligatorio**: la BD está en Internet. Usa `sslmode=require` como mínimo;
  `verify-full` con el CA del proveedor es lo ideal (incluye el `.crt` en el jar
  o en una Layer y referéncialo con `sslrootcert`).
- **IP allowlist**: una Lambda **fuera de VPC** sale a Internet con IPs de AWS variables
  y no se puede hacer allowlist. Si el Postgres exige allowlist:
  1. Despliega la Lambda en **subnets privadas** (`SubnetIds`, `SecurityGroupIds` en el template).
  2. Las subnets deben enrutar `0.0.0.0/0` a un **NAT Gateway con Elastic IP**.
  3. Agrega esa EIP al allowlist del Postgres (y el SG debe permitir salida al puerto 5432).
  El NAT Gateway tiene costo por hora y por GB.
- **Pool pequeño**: cada instancia de Lambda atiende una petición a la vez, así que el pool
  es `maximum-pool-size: 2`, `minimum-idle: 0`, `connection-timeout: 5s`,
  `max-lifetime: 5 min`. Con mucha concurrencia, el número de conexiones es aproximadamente
  2 × instancias. Limita `ReservedConcurrentExecutions` o pon un pooler (PgBouncer) delante
  del Postgres si su `max_connections` es bajo.
- **Latencia**: cada query cruza Internet. Elige la región de AWS más cercana a la BD.

## Seguridad de dependencias

| Pieza | Qué hace | Cuándo |
|---|---|---|
| **Dependabot** (`.github/dependabot.yml`) | Abre PRs que **arreglan** dependencias vulnerables o desactualizadas, además de las imágenes base del `Dockerfile` y las GitHub Actions | En cuanto sale un aviso (security updates) y cada lunes (version updates) |
| **Dependency scan** (`.github/workflows/dependency-scan.yml`) | OSV-Scanner revisa `pom.xml` (directas y transitivas, resueltas contra Maven Central), **comenta el resultado en el PR** y lo **falla** si hay vulnerabilidades conocidas | Cada PR, cada push a `main` y cada lunes |
| **CodeQL** (`.github/workflows/codeql.yml`) | Analiza el código Java (no las dependencias) con las consultas `security-extended` y publica las alertas en **Security → Code scanning** | Cada PR, cada push a `main`, diario a las 7:00 (Ciudad de México) y a mano (*Run workflow*). Se enciende con la variable `CODEQL_ENABLED=true` |

Para que Dependabot abra los PRs de seguridad, activa en **Settings → Code security**:
*Dependency graph*, *Dependabot alerts* y *Dependabot security updates* (gratis en repos privados).

Escaneo local, igual que en CI:
```bash
docker run --rm -v "$PWD:/src:ro" ghcr.io/google/osv-scanner:v2.6.0 scan source -r --data-source=native /src
```
