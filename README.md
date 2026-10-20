# b-sheet — backend de login (sheets-tt)

Backend de autenticación de **sheets-tt**: Micronaut 4 + PostgreSQL, empaquetado para
**AWS Lambda (runtime administrado `java25`, arm64) con SnapStart**, detrás de **API Gateway HTTP API
(payload v2)**. La base de datos vive **fuera de AWS**.

Implementa el contrato `API_CONTRACT.md` (v1): `GET /health`, `POST /auth/login`, `GET /me`.

## Arquitectura

```
Web (S3 + CloudFront) ──/api/*──► CloudFront ──► API Gateway HTTP API ($default, payload 2.0)
Móvil (Flutter) ─────────────────────────────────►        │  ANY /{proxy+}
                                                          ▼
                                    Lambda java25 arm64 + SnapStart (alias "live")
                                    handler: io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction
                                                          │  JDBC + SSL (Hikari, pool máx. 2)
                                                          ▼
                                          PostgreSQL externo (fuera de AWS)
```

| Pieza | Detalle |
|---|---|
| Framework | Micronaut 4.10.20 (`micronaut-parent`, `micronaut.runtime=lambda`) |
| Build | Maven 3.9.11 (wrapper `./mvnw`), Java 25, jar "fat" con `maven-shade-plugin` |
| Persistencia | Micronaut Data JDBC + HikariCP + Flyway (`src/main/resources/db/migration`) |
| Seguridad | Micronaut Security JWT, HS256 (`JWT_SECRET`), contraseñas bcrypt (jBCrypt) |
| SnapStart | `micronaut-crac`: suspende el pool Hikari antes del snapshot y lo refresca al restaurar |

Código:

- `tt.sheets.HealthController` — `GET /health` → `{"status":"UP"}` (no toca la BD).
- `tt.sheets.auth.AuthController` — `POST /auth/login`: busca la cuenta por correo
  (case-insensitive, solo `estado_cuenta = 'ACTIVA'`), valida bcrypt (tiempo constante aunque la
  cuenta no exista) y emite el JWT con el `rol` de la cuenta en `roles`.
  200 con `access_token`, `token_type`, `expires_in`, `username`, `roles`; 401 `{"message": "..."}`;
  400 si faltan campos o están vacíos.
- `tt.sheets.auth.MeController` — `GET /me` (Bearer) → `{"username": correo, "displayName":
  nombre_completo}`; 401 sin token, con token inválido o expirado, o si la cuenta ya no está activa.
- `tt.sheets.cuenta` — entidad `Cuenta` (tabla `cuenta`) y repositorio.
- Migraciones (ver [Base de datos](#base-de-datos-flyway)): `db/migration/V1__esquema_inicial.sql`
  (esquema completo del ER) y, **solo en desarrollo y tests**, `db/demo/V2__cuenta_demo.sql`
  (cuenta demo `demo@sheets.tt` / `Demo1234!`, "Usuario Demo").

## Base de datos (Flyway)

El esquema vive en `src/main/resources/db/migration` y lo aplica **Flyway**. `V1__esquema_inicial.sql`
crea las tablas del diagrama ER "Sistema móvil y web":

| Tabla | Relaciones |
|---|---|
| `cuenta` | Login: `correo` único sin distinguir mayúsculas, `contrasena_hash` (bcrypt), `rol` (`ADMIN`/`USUARIO`), `estado_cuenta` (`ACTIVA`/`INACTIVA`/`BLOQUEADA`) |
| `dispositivo` | `identificador_unico` único, `estado_dispositivo` (`ACTIVO`/`INACTIVO`/`REVOCADO`) |
| `cuenta_dispositivo` | → `cuenta`, → `dispositivo`. Historial de asociaciones; solo una vigente (`fecha_desasociacion IS NULL`) por pareja |
| `arbol` | → `cuenta`, → `dispositivo`. Coordenadas `NUMERIC(9,6)` con rango validado |
| `zona_follaje` | → `cuenta`, → `dispositivo`. Centro + `radio_metros > 0` |
| `analisis` | → `zona_follaje` (obligatoria), → `arbol` (**opcional**). `estado_sincronizacion` (`PENDIENTE`/`SINCRONIZADO`/`ERROR`), `confianza` 0–1 |
| `analisis_cromatico` | → `analisis`. `pct_sano`, `pct_regular`, `pct_danado`, `pct_incierto` (0–100) |

Hay dos carpetas de migraciones:

| Carpeta | Contenido | Dónde se aplica |
|---|---|---|
| `db/migration` | Esquema (`V1`, `V3`, …) | Siempre: producción (Neon), desarrollo y tests |
| `db/demo` | Datos de prueba: cuenta demo (`V2`) | Solo desarrollo (`application-dev.yml`) y tests (`application-test.yml`) |

`ProductionMigrationsTest` falla si alguna migración de `db/migration` inserta cuentas, porque esas
llegan a producción.

Las migraciones se aplican de dos formas, y las dos usan la misma tabla `flyway_schema_history`
(Flyway 10.22.0 en ambos casos):

1. **Flyway en Docker** (`docker-compose.yml`), para construir o actualizar una base sin arrancar
   el backend:
   ```bash
   # Local: Postgres 17 en localhost:5434 (usuario/clave sheets/sheets), con db/migration + db/demo
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
2. **Al arrancar el backend** (`FLYWAY_ENABLED=true`, el default): Micronaut aplica lo que falte.
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
| `DB_PASSWORD` | sí (prod) | vacío | Contraseña de la BD |
| `JWT_SECRET` | **sí** | solo hay default en entorno `dev` y `test` | Secreto HS256, **≥ 32 caracteres**. Sin él, la app no arranca (fail-fast) |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Orígenes permitidos, separados por comas |
| `CONTEXT_PATH` | no | vacío | Prefijo de rutas. Vacío en el despliegue estándar (ver «Rutas `/api`»); `/api` solo sin la CloudFront Function |
| `FLYWAY_ENABLED` | no | `true` | Ejecutar migraciones al arrancar |
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
case-insensitive, 401, 400, `/me` con y sin token, token inválido, CORS y `CONTEXT_PATH=/api`.

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

`template.yaml` define la función (imagen de contenedor java25 en arm64/Graviton, 1024 MB, timeout 20 s, **SnapStart en
`PublishedVersions`** con alias `live`), el HTTP API (`ANY /{proxy+}`, stage `$default`,
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
- **SnapStart**: solo aplica a versiones publicadas; el evento HttpApi apunta al alias `live`.
  `micronaut-crac` suspende Hikari antes del snapshot (`allow-pool-suspension: true`), así que
  no se restauran conexiones TCP muertas. Flyway corre durante la inicialización, es decir,
  al publicar la versión: **la BD debe ser alcanzable al desplegar**. Si no lo es, despliega
  con `FlywayEnabled=false` y migra aparte con Flyway en Docker (ver [Base de datos](#base-de-datos-flyway)).
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
