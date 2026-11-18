#!/usr/bin/env bash
# =============================================================================================
# Pruebas de estrés de b-sheet (k6 en Docker) con historial en tablas fuera de la aplicación.
#
#   ./estres/correr.sh levantar             BD propia sheets_estres (nueva) + b-sheet local en :8085
#   ./estres/correr.sh sembrar [análisis]   agrega volumen a sheets_estres (por defecto 20 000 análisis)
#   ./estres/correr.sh limpiar              borra ese volumen (db/demo queda igual)
#   ./estres/correr.sh <escenario> [URL]    corre humo | carga | estres | pico | login
#   ./estres/correr.sh detener              apaga el b-sheet que levantó `levantar`
#
# Usa su propia BD (sheets_estres en el Postgres de docker compose): no toca la BD «sheets» de
# desarrollo ni la del E2E. Por defecto apunta al b-sheet local (http://localhost:8085). Cada corrida deja:
#   estres/resultados/crudos/<id>.json      resumen completo de k6 (no se versiona)
#   estres/resultados/<id>.md               tabla por endpoint de esa corrida
#   estres/resultados/historial.csv         una fila por corrida (para hojas de cálculo)
#   estres/resultados/historial.md          la misma tabla en Markdown
#
# No apunta a AWS por accidente: una URL que no sea localhost exige ESTRES_REMOTO=1 y confirmar.
# Compatible con el bash 3.2 de macOS.
# =============================================================================================
set -euo pipefail

DIR=$(cd "$(dirname "$0")" && pwd)
REPO=$(cd "$DIR/.." && pwd)
RES="$DIR/resultados"
K6_IMAGEN=grafana/k6:0.57.0
PUERTO=${PORT:-8085}

if [ -t 1 ]; then V=$'\033[32m'; A=$'\033[33m'; R=$'\033[31m'; X=$'\033[0m'; else V= A= R= X=; fi
ok()     { printf '%s✔ %s%s\n' "$V" "$1" "$X"; }
aviso()  { printf '%s! %s%s\n' "$A" "$1" "$X"; }
fallar() { printf '%s✘ %s%s\n' "$R" "$1" "$X" >&2; exit 1; }

ayuda() { sed -n '3,21p' "$0" | sed 's/^# \{0,1\}//'; exit 1; }

BD=sheets_estres
PID="$RES/backend.pid"

psql_local() {
  (cd "$REPO" && docker compose exec -T db psql -U "${DB_USER:-sheets}" -d "$BD" -q "$@")
}

cmd_levantar() {
  if curl -fs -o /dev/null --max-time 3 "http://localhost:$PUERTO/health"; then
    fallar "Ya hay algo respondiendo en :$PUERTO. Apágalo ($0 detener) o usa otro PORT."
  fi
  (cd "$REPO" && docker compose up -d --wait db >/dev/null)
  (cd "$REPO" && docker compose exec -T db psql -U "${DB_USER:-sheets}" -d sheets -q \
    -c "DROP DATABASE IF EXISTS $BD" -c "CREATE DATABASE $BD")
  ok "BD $BD recreada"
  # En AWS cada Lambda atiende 1 petición a la vez con un pool de 2 y escala por instancias. Aquí hay
  # un solo servidor para todos los usuarios virtuales: con pool 2 se mide sobre todo la cola del pool.
  echo "${ESTRES_POOL:-10}" > "$RES/pool"
  mkdir -p "$RES"
  if [ -x /usr/libexec/java_home ] && /usr/libexec/java_home -v 25 >/dev/null 2>&1; then
    JAVA_HOME=$(/usr/libexec/java_home -v 25); export JAVA_HOME
  fi
  # Perfil dev: Flyway aplica db/migration + db/demo (cuentas admin@sheets.tt / demo@sheets.tt).
  # Credenciales de AWS ficticias: la firma de evidencias es local y nada sale a AWS.
  (cd "$REPO" && DB_URL="jdbc:postgresql://localhost:${DB_PORT:-5434}/$BD" DB_USER="${DB_USER:-sheets}" \
    DB_PASSWORD="${DB_PASSWORD:-sheets}" PORT="$PUERTO" AWS_ACCESS_KEY_ID=AKIAESTRESFICTICIA \
    AWS_SECRET_ACCESS_KEY=estres-ficticio AWS_REGION=us-east-1 AWS_PROFILE= \
    DATASOURCES_DEFAULT_MAXIMUM_POOL_SIZE="${ESTRES_POOL:-10}" \
    nohup ./mvnw -q mn:run -Pdev < /dev/null > "$RES/backend.log" 2>&1 & echo $! > "$PID")
  local i
  for i in $(seq 1 120); do
    curl -fs -o /dev/null --max-time 2 "http://localhost:$PUERTO/health" && { ok "b-sheet en http://localhost:$PUERTO (log: estres/resultados/backend.log)"; return; }
    sleep 2
  done
  fallar "b-sheet no arrancó en 4 min; revisa estres/resultados/backend.log"
}

cmd_detener() {
  if [ -f "$PID" ]; then
    pkill -P "$(cat "$PID")" 2>/dev/null || true
    kill "$(cat "$PID")" 2>/dev/null || true
    rm -f "$PID"
  fi
  # mvn deja el proceso java como hijo de otro: se busca por el puerto.
  local java
  java=$(lsof -nP -t -iTCP:"$PUERTO" -sTCP:LISTEN 2>/dev/null || true)
  [ -z "$java" ] || kill $java 2>/dev/null || true
  ok "b-sheet de estrés detenido"
}

cmd_sembrar() {
  local analisis=${1:-20000}
  case "$analisis" in ''|*[!0-9]*) fallar "La cantidad de análisis debe ser un número." ;; esac
  local arboles=$(( analisis / 50 + 10 )) zonas=$(( analisis / 100 + 5 ))
  aviso "Agrega $arboles árboles, $zonas zonas y $analisis análisis a la BD LOCAL (Docker, perfil dev)."
  psql_local -v arboles="$arboles" -v zonas="$zonas" -v analisis="$analisis" < "$DIR/sembrar.sql"
  ok "Volumen sembrado"
}

cmd_limpiar() {
  psql_local < "$DIR/limpiar.sql"
  ok "Volumen de estrés borrado"
}

datos_locales() {
  psql_local -At -c "select count(*) from analisis" 2>/dev/null | tr -d '[:space:]' || true
}

cmd_correr() {
  local escenario=$1 url=${2:-http://localhost:$PUERTO}
  case "$escenario" in humo|carga|estres|pico|login) ;; *) ayuda ;; esac
  url=${url%/}

  case "$url" in
    http://localhost:*|http://127.0.0.1:*) local remoto=0 ;;
    *) local remoto=1 ;;
  esac
  if [ "$remoto" = 1 ]; then
    [ "${ESTRES_REMOTO:-0}" = 1 ] || fallar "$url no es local. Para apuntar a otro ambiente: ESTRES_REMOTO=1 $0 $escenario $url"
    aviso "Vas a generar carga contra $url. En AWS esto consume Lambda/API Gateway/RDS y afecta a quien lo use."
    printf '%s? Escribe el dominio para confirmar: %s' "$A" "$X"
    local dominio; read -r dominio
    case "$url" in *"$dominio"*) [ -n "$dominio" ] || fallar "Cancelado." ;; *) fallar "Cancelado." ;; esac
  fi

  curl -fsS -o /dev/null --max-time 15 "$url/health" || fallar "b-sheet no responde en $url/health. Levántalo con: $0 levantar"

  # Dentro del contenedor de k6, «localhost» es el contenedor: se usa host.docker.internal.
  local url_k6=${url/localhost/host.docker.internal}
  url_k6=${url_k6/127.0.0.1/host.docker.internal}

  local sello id commit datos
  sello=$(date +%Y-%m-%d_%H%M%S)
  id="${sello}_${escenario}"
  commit=$(git -C "$REPO" rev-parse --short HEAD 2>/dev/null || echo "—")
  if [ "$remoto" = 0 ]; then datos=$(datos_locales); else datos="—"; fi
  [ -n "$datos" ] || datos="—"
  mkdir -p "$RES/crudos"

  ok "Escenario $escenario contra $url (análisis en la BD: $datos)"
  local salida=0
  docker run --rm -i \
    -v "$DIR:/scripts:ro" -v "$RES:/resultados" \
    -e URL="$url_k6" -e ESCENARIO="$escenario" -e ID_CORRIDA="$id" \
    ${ADMIN:+-e ADMIN="$ADMIN"} ${EXPERTO:+-e EXPERTO="$EXPERTO"} ${CONTRASENA:+-e CONTRASENA="$CONTRASENA"} \
    "$K6_IMAGEN" run --quiet /scripts/escenarios.js || salida=$?
  # 99 = algún umbral no se cumplió: la corrida es válida y se anota igual.
  [ "$salida" = 0 ] || [ "$salida" = 99 ] || fallar "k6 terminó con código $salida (ver arriba)."

  local json="$RES/crudos/$id.json"
  [ -f "$json" ] || fallar "k6 no dejó el resumen en $json"
  anotar "$json" "$id" "$escenario" "$url" "$commit" "$datos" "$salida"
}

anotar() {
  local json=$1 id=$2 escenario=$3 url=$4 commit=$5 datos=$6 salida=$7
  local remoto_md=0
  case "$url" in http://localhost:*|http://127.0.0.1:*) ;; *) remoto_md=1 ;; esac
  local fila
  fila=$(jq -r --arg id "$id" --arg esc "$escenario" --arg url "$url" --arg commit "$commit" --arg datos "$datos" \
    --arg umbrales "$([ "$salida" = 0 ] && echo sí || echo no)" '
    .metrics as $m
    | ($m.http_req_duration.values) as $d
    | [ $id, $esc, $url, $commit, $datos,
        ($m.vus_max.values.max // 0),
        ($m.http_reqs.values.count // 0),
        (($m.http_reqs.values.rate // 0) * 10 | round / 10),
        (($d.med // 0) | round), (($d["p(95)"] // 0) | round), (($d["p(99)"] // 0) | round), (($d.max // 0) | round),
        ((($m.http_req_failed.values.rate // 0) * 10000 | round) / 100),
        $umbrales ]
    | @tsv' "$json")

  local csv="$RES/historial.csv" md="$RES/historial.md"
  [ -f "$csv" ] || echo "id,escenario,url,commit,analisis_bd,vus_max,peticiones,rps,p50_ms,p95_ms,p99_ms,max_ms,errores_pct,umbrales_ok" > "$csv"
  printf '%s\n' "$fila" | tr '\t' ',' >> "$csv"
  if [ ! -f "$md" ]; then
    cat > "$md" <<'EOF'
# Historial de pruebas de estrés de b-sheet

Una fila por corrida de `./estres/correr.sh`. Tiempos en milisegundos; errores = peticiones fallidas
(HTTP ≥ 400 o sin respuesta). «Umbrales» = p95 < 800 ms, login p95 < 1500 ms y errores < 1 %.
El detalle por endpoint de cada corrida está en `estres/resultados/<id>.md`.

| Corrida | Escenario | URL | Commit | Análisis en BD | VUs máx | Peticiones | Pet./s | p50 | p95 | p99 | Máx | Errores % | Umbrales |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
EOF
  fi
  printf '%s\n' "$fila" | awk -F'\t' '{printf "| [%s](%s.md) | %s | %s | `%s` | %s | %s | %s | %s | %s | %s | %s | %s | %s | %s |\n", $1,$1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14}' >> "$md"

  # Detalle por endpoint de esta corrida.
  {
    echo "# Corrida $id"
    echo
    echo "- Escenario: \`$escenario\` · URL: $url · commit \`$commit\` · análisis en la BD: $datos"
    [ ! -f "$RES/pool" ] || [ "$remoto_md" = 1 ] || echo "- b-sheet local con pool de BD de $(cat "$RES/pool") conexiones (en AWS: 2 por Lambda, 1 petición por instancia)"
    echo "- Umbrales cumplidos: $([ "$salida" = 0 ] && echo sí || echo no) · resumen completo: \`crudos/$id.json\`"
    echo
    echo "| Endpoint | Peticiones | Errores % | p50 | p95 | p99 | Máx |"
    echo "|---|---:|---:|---:|---:|---:|---:|"
    jq -r '
      .metrics as $m
      | $m | to_entries[]
      | select(.key | test("^http_req_duration\\{nombre:"))
      | (.key | capture("nombre:(?<n>[^}]+)").n) as $n
      | ($m["http_reqs{nombre:\($n)}"].values.count // 0) as $c
      | select($c > 0)
      | ((($m["http_req_failed{nombre:\($n)}"].values.rate // 0) * 10000 | round) / 100) as $e
      | .value.values as $v
      | "| \($n) | \($c) | \($e) | \($v.med | round) | \($v["p(95)"] | round) | \($v["p(99)"] | round) | \($v.max | round) |"' "$json" | sort
  } > "$RES/$id.md"

  ok "Anotado en estres/resultados/historial.md y estres/resultados/$id.md"
  [ "$salida" = 0 ] || aviso "Algún umbral no se cumplió (ver la tabla); es lo esperado en estrés y pico."
}

for h in docker curl jq git; do command -v "$h" >/dev/null || fallar "Falta '$h' en el sistema."; done
case "${1:-}" in
  levantar) cmd_levantar ;;
  detener) cmd_detener ;;
  sembrar) shift; cmd_sembrar "${1:-}" ;;
  limpiar) cmd_limpiar ;;
  humo|carga|estres|pico|login) cmd_correr "$@" ;;
  *) ayuda ;;
esac
