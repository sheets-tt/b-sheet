# b-sheet — backend de login (sheets-tt)

Backend de autenticación de **sheets-tt**: Micronaut 4 + PostgreSQL, empaquetado para
**AWS Lambda (runtime administrado `java21`) con SnapStart**, detrás de **API Gateway HTTP API
(payload v2)**. La base de datos vive **fuera de AWS**.

Implementa el contrato `API_CONTRACT.md` (v1): `GET /health`, `POST /auth/login`, `GET /me`.

## Arquitectura

```
Web (S3 + CloudFront) ──/api/*──► CloudFront ──► API Gateway HTTP API ($default, payload 2.0)
Móvil (Flutter) ─────────────────────────────────►        │  ANY /{proxy+}
                                                          ▼
                                    Lambda java21 + SnapStart (alias "live")
                                    handler: io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction
                                                          │  JDBC + SSL (Hikari, pool máx. 2)
                                                          ▼
                                          PostgreSQL externo (fuera de AWS)
```

| Pieza | Detalle |
|---|---|
| Framework | Micronaut 4.10.20 (plugin `io.micronaut.application` 4.6.2, runtime `lambda_java`) |
| Build | Gradle 8.14.3 (wrapper) con Kotlin DSL, Java 21, jar "fat" con Shadow |
| Persistencia | Micronaut Data JDBC + HikariCP + Flyway (`src/main/resources/db/migration`) |
| Seguridad | Micronaut Security JWT, HS256 (`JWT_SECRET`), contraseñas bcrypt (jBCrypt) |
| SnapStart | `micronaut-crac`: suspende el pool Hikari antes del snapshot y lo refresca al restaurar |

Código:

- `tt.sheets.HealthController` — `GET /health` → `{"status":"UP"}` (no toca la BD).
- `tt.sheets.auth.AuthController` — `POST /auth/login`: busca el usuario (case-insensitive),
  valida bcrypt (tiempo constante aunque el usuario no exista) y emite el JWT.
  200 con `access_token`, `token_type`, `expires_in`, `username`, `roles`; 401 `{"message": "..."}`;
  400 si faltan campos o están vacíos.
- `tt.sheets.auth.MeController` — `GET /me` (Bearer) → `{"username","displayName"}`; 401 sin token
  o con token inválido o expirado.
- `tt.sheets.user` — entidad `users` y repositorio.
- Migraciones: `V1__create_users.sql` (tabla + índice único `lower(username)`),
  `V2__demo_user.sql` (usuario demo `demo@sheets.tt` / `Demo1234!`, "Usuario Demo").

## Variables de entorno

| Variable | Obligatoria | Default | Descripción |
|---|---|---|---|
| `DB_URL` | sí (prod) | `jdbc:postgresql://localhost:5432/sheets` | JDBC URL. En producción usar SSL: `?sslmode=require` o `?sslmode=verify-full&sslrootcert=...` |
| `DB_USER` | sí (prod) | `postgres` (en `run` y tests: tu usuario del SO) | Usuario de la BD |
| `DB_PASSWORD` | sí (prod) | vacío | Contraseña de la BD |
| `JWT_SECRET` | **sí** | solo hay default en entorno `dev` y `test` | Secreto HS256, **≥ 32 caracteres**. Sin él, la app no arranca (fail-fast) |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Orígenes permitidos, separados por comas |
| `CONTEXT_PATH` | no | vacío | Prefijo de rutas (p.ej. `/api`) |
| `FLYWAY_ENABLED` | no | `true` | Ejecutar migraciones al arrancar |
| `PORT` | no | `8080` | Puerto local (solo `./gradlew run`) |
| `LOG_LEVEL` | no | `INFO` | Nivel de log raíz |
| `DB_TEST_URL` | no | `jdbc:postgresql://localhost:5432/sheets_test` | BD que usan los tests |

Nunca se commitean secretos: `.env` y `*.local.env` están en `.gitignore`.

## Desarrollo local

Requisitos: JDK 21 y PostgreSQL local.

```bash
createdb sheets          # BD de desarrollo
createdb sheets_test     # BD de tests

./gradlew run            # entorno "dev": JWT_SECRET de desarrollo, DB_USER = tu usuario del SO
# otro puerto:  PORT=18080 ./gradlew run
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

> En `./gradlew run` se usa Netty como servidor (dependencia `developmentOnly`; no entra al jar
> de Lambda). El servidor de pruebas `micronaut-function-aws-api-proxy-test` 4.11 está excluido
> porque, junto con `micronaut-servlet-core` 5.6, intenta abrir el puerto dos veces.

### Tests

```bash
./gradlew test
```

Los tests (JUnit 5) **invocan el handler real de Lambda** (`APIGatewayV2HTTPEventFunction`)
con eventos `APIGatewayV2HTTPEvent` (payload 2.0), con el mismo builder de contexto que usa
AWS, contra la BD `sheets_test`. Cubren health, login OK (payload exacto del contrato), login
case-insensitive, 401, 400, `/me` con y sin token, token inválido, CORS y `CONTEXT_PATH=/api`.

## Empaquetado para Lambda

```bash
./gradlew shadowJar
# → build/libs/sheets-backend-lambda.jar
```

- Runtime: `java21`
- Handler: `io.micronaut.function.aws.proxy.payload2.APIGatewayV2HTTPEventFunction`

## Despliegue con AWS SAM

`template.yaml` define la función (java21, 1024 MB, timeout 20 s, **SnapStart en
`PublishedVersions`** con alias `live`), el HTTP API (`ANY /{proxy+}`, stage `$default`,
payload 2.0) y un log group con retención de 14 días.

```bash
./gradlew shadowJar
sam validate
sam deploy --guided \
  --stack-name sheets-backend \
  --capabilities CAPABILITY_IAM \
  --parameter-overrides \
    DbUrl='jdbc:postgresql://db.example.com:5432/sheets?sslmode=require' \
    DbUser=sheets DbPassword='***' JwtSecret='***(>=32)***' \
    CorsAllowedOrigins='https://app.example.com' ContextPath='/api'
```

No hace falta `sam build`: `CodeUri` apunta directamente al jar que genera Gradle. La salida
`ApiDomain` es el origen que CloudFront debe usar para `/api/*`.

Notas:

- **Secretos**: `DbPassword` y `JwtSecret` son parámetros `NoEcho`, pero quedan como variables
  de entorno de la función. Para producción conviene moverlos a SSM Parameter Store
  o Secrets Manager.
- **Rutas `/api`**: CloudFront no quita prefijos por sí solo. Hay dos opciones:
  `ContextPath=/api` (el backend sirve `/api/health`, `/api/auth/login`, ...) o una
  CloudFront Function que reescriba la URI y deje `ContextPath` vacío. Los clientes que
  llaman directo a API Gateway (móvil) deben usar la misma base.
- **CORS** lo resuelve Micronaut. No configures CORS en el HTTP API, porque API Gateway
  respondería los preflight con otras reglas.
- **SnapStart**: solo aplica a versiones publicadas; el evento HttpApi apunta al alias `live`.
  `micronaut-crac` suspende Hikari antes del snapshot (`allow-pool-suspension: true`), así que
  no se restauran conexiones TCP muertas. Flyway corre durante la inicialización, es decir,
  al publicar la versión: **la BD debe ser alcanzable al desplegar**. Si no lo es, despliega
  con `FlywayEnabled=false` y migra aparte.
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
