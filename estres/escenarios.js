// Pruebas de estrés de b-sheet con k6 (https://k6.io). Se corren con ./estres/correr.sh, que usa la
// imagen de Docker de k6 y anota cada corrida en estres/resultados/historial.{csv,md}.
//
// Escenarios (ESCENARIO):
//   humo    1 usuario, 30 s: ¿responde todo? Sirve antes de cualquier otra corrida.
//   carga   uso normal sostenido: sube a 20 usuarios y se queda 2 min.
//   estres  sube por escalones (25 → 50 → 100 → 150) para ver dónde empieza a degradarse.
//   pico    de 5 a 200 usuarios de golpe durante 30 s y de vuelta: ¿se recupera?
//   login   tasa fija de inicios de sesión (bcrypt cuesta CPU a propósito).
//
// Cada usuario virtual hace lo que haría una persona en la web: perfil, Resumen (estadísticas y
// análisis recientes), mapa (árboles y zonas), un seguimiento, filtros de Análisis y, si es
// administrador, la lista de cuentas. Cada petición lleva la etiqueta `nombre` para tener tiempos
// por endpoint en el resumen.
import http from 'k6/http'
import { check, group, sleep } from 'k6'
import { Rate } from 'k6/metrics'

const BASE = (__ENV.URL || 'http://host.docker.internal:8085').replace(/\/$/, '')
const ESCENARIO = __ENV.ESCENARIO || 'humo'
const ADMIN = __ENV.ADMIN || 'admin@sheets.tt'
const EXPERTO = __ENV.EXPERTO || 'demo@sheets.tt'
const CONTRASENA = __ENV.CONTRASENA || 'Demo1234!'
const ID = __ENV.ID_CORRIDA || `${ESCENARIO}-${Date.now()}`

const erroresNegocio = new Rate('errores_negocio')

const ESCENARIOS = {
  humo: { executor: 'constant-vus', vus: 1, duration: '30s' },
  carga: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 20 },
      { duration: '2m', target: 20 },
      { duration: '20s', target: 0 },
    ],
  },
  estres: {
    executor: 'ramping-vus',
    startVUs: 0,
    stages: [
      { duration: '30s', target: 25 },
      { duration: '1m', target: 25 },
      { duration: '30s', target: 50 },
      { duration: '1m', target: 50 },
      { duration: '30s', target: 100 },
      { duration: '1m', target: 100 },
      { duration: '30s', target: 150 },
      { duration: '1m', target: 150 },
      { duration: '30s', target: 0 },
    ],
  },
  pico: {
    executor: 'ramping-vus',
    startVUs: 5,
    stages: [
      { duration: '20s', target: 5 },
      { duration: '10s', target: 200 },
      { duration: '30s', target: 200 },
      { duration: '10s', target: 5 },
      { duration: '40s', target: 5 },
    ],
  },
  login: {
    executor: 'constant-arrival-rate',
    rate: 20,
    timeUnit: '1s',
    duration: '1m',
    preAllocatedVUs: 20,
    maxVUs: 100,
    exec: 'soloLogin',
  },
}

if (!ESCENARIOS[ESCENARIO]) {
  throw new Error(`Escenario desconocido: ${ESCENARIO}. Usa: ${Object.keys(ESCENARIOS).join(', ')}`)
}

// Umbrales: lo que se espera del sistema con uso normal. En estrés y pico se registran (sirven
// para ver dónde se rompe) pero no detienen la corrida.
const UMBRALES = {
  http_req_failed: [{ threshold: 'rate<0.01', abortOnFail: false }],
  errores_negocio: [{ threshold: 'rate<0.01', abortOnFail: false }],
  http_req_duration: [{ threshold: 'p(95)<800', abortOnFail: false }],
  'http_req_duration{nombre:login}': [{ threshold: 'p(95)<1500', abortOnFail: false }],
}

export const options = {
  scenarios: { [ESCENARIO]: ESCENARIOS[ESCENARIO] },
  thresholds: UMBRALES,
  summaryTrendStats: ['avg', 'min', 'med', 'p(90)', 'p(95)', 'p(99)', 'max'],
  // Los nombres de endpoint van en la etiqueta `nombre`, no en la URL con ids.
  systemTags: ['status', 'method', 'name', 'scenario', 'expected_response'],
}

function login(correo) {
  const res = http.post(`${BASE}/auth/login`, JSON.stringify({ username: correo, password: CONTRASENA }), {
    headers: { 'Content-Type': 'application/json' },
    tags: { nombre: 'login' },
  })
  const ok = check(res, { 'login 200': (r) => r.status === 200 })
  erroresNegocio.add(!ok)
  return ok ? res.json('access_token') : null
}

export function setup() {
  const salud = http.get(`${BASE}/health`)
  if (salud.status !== 200) {
    throw new Error(`b-sheet no responde en ${BASE}/health (status ${salud.status}). ¿Está corriendo?`)
  }
  const admin = login(ADMIN)
  const experto = login(EXPERTO)
  if (!admin || !experto) throw new Error('No se pudo iniciar sesión con las cuentas de prueba (¿perfil dev con db/demo?).')
  const auth = { headers: { Authorization: `Bearer ${admin}` } }
  const arboles = http.get(`${BASE}/arboles`, auth).json() || []
  const zonas = http.get(`${BASE}/zonas`, auth).json() || []
  const analisis = http.get(`${BASE}/analisis?limite=100`, auth).json() || []
  return {
    tokens: { admin, experto },
    arboles: arboles.map((a) => a.id).slice(0, 200),
    zonas: zonas.map((z) => z.id).slice(0, 200),
    analisis: analisis.map((a) => a.id).slice(0, 200),
  }
}

const azar = (lista) => (lista.length ? lista[Math.floor(Math.random() * lista.length)] : null)

// `validos`: estados que son una respuesta correcta del negocio (p. ej. 404 = análisis sin foto).
function get(ruta, token, nombre, validos = [200]) {
  const res = http.get(`${BASE}${ruta}`, {
    headers: { Authorization: `Bearer ${token}` },
    tags: { nombre },
    responseCallback: http.expectedStatuses(...validos),
  })
  const ok = check(res, { [`${nombre} ${validos.join('/')}`]: (r) => validos.includes(r.status) })
  erroresNegocio.add(!ok)
  return res
}

// Una "visita" de una persona a la web. 1 de cada 4 usuarios virtuales es administrador.
export default function (datos) {
  const esAdmin = __VU % 4 === 0
  const token = esAdmin ? datos.tokens.admin : datos.tokens.experto

  group('resumen', () => {
    get('/me', token, 'me')
    get('/estadisticas', token, 'estadisticas')
    get('/analisis?limite=500', token, 'analisis_resumen')
  })
  sleep(1)

  group('mapa', () => {
    get('/arboles', token, 'arboles')
    get('/zonas', token, 'zonas')
  })
  sleep(1)

  group('seguimiento', () => {
    const arbol = azar(datos.arboles)
    if (arbol) get(`/arboles/${arbol}`, token, 'arbol')
    const zona = azar(datos.zonas)
    if (zona) get(`/zonas/${zona}`, token, 'zona')
  })
  sleep(1)

  group('analisis', () => {
    get('/analisis?limite=100', token, 'analisis')
    get('/analisis?limite=100&tipo=corteza&resultado=infestado', token, 'analisis_filtrado')
    const uno = azar(datos.analisis)
    if (uno) {
      get(`/analisis/${uno}`, token, 'analisis_detalle')
      get(`/analisis/${uno}/evidencia`, token, 'evidencia_firmada', [200, 404]) // 404 = sin foto
    }
  })

  if (esAdmin) {
    group('administracion', () => {
      get('/cuentas', token, 'cuentas')
      get('/dispositivos', token, 'dispositivos')
    })
  }
  sleep(1 + Math.random() * 2)
}

export function soloLogin() {
  login(Math.random() < 0.25 ? ADMIN : EXPERTO)
}

// Guarda el resumen completo en JSON (correr.sh lo convierte en filas del historial) y deja el
// texto habitual de k6 en la consola.
export function handleSummary(datos) {
  return {
    [`/resultados/crudos/${ID}.json`]: JSON.stringify(datos, null, 2),
    stdout: textoResumen(datos),
  }
}

function textoResumen(d) {
  const m = d.metrics
  const dur = m.http_req_duration?.values || {}
  const fmt = (v) => (v === undefined ? '—' : `${v.toFixed(1)} ms`)
  const lineas = [
    `\nEscenario ${ESCENARIO} · ${BASE}`,
    `Peticiones: ${m.http_reqs?.values.count ?? 0} (${(m.http_reqs?.values.rate ?? 0).toFixed(1)}/s)`,
    `Fallidas: ${((m.http_req_failed?.values.rate ?? 0) * 100).toFixed(2)} %`,
    `Duración: p50 ${fmt(dur.med)} · p95 ${fmt(dur['p(95)'])} · p99 ${fmt(dur['p(99)'])} · máx ${fmt(dur.max)}`,
    'Por endpoint (p95):',
  ]
  for (const [nombre, metrica] of Object.entries(m)) {
    const r = /^http_req_duration\{nombre:(.+)\}$/.exec(nombre)
    if (r) lineas.push(`  ${r[1].padEnd(20)} ${fmt(metrica.values['p(95)'])}`)
  }
  return lineas.join('\n') + '\n'
}

// Para que k6 calcule tiempos y conteos por endpoint (k6 solo separa por etiqueta las métricas con umbral).
for (const nombre of ['login', 'me', 'estadisticas', 'analisis_resumen', 'arboles', 'zonas', 'arbol', 'zona',
  'analisis', 'analisis_filtrado', 'analisis_detalle', 'evidencia_firmada', 'cuentas', 'dispositivos']) {
  if (!options.thresholds[`http_req_duration{nombre:${nombre}}`]) {
    options.thresholds[`http_req_duration{nombre:${nombre}}`] = [{ threshold: 'max>=0', abortOnFail: false }]
  }
  options.thresholds[`http_reqs{nombre:${nombre}}`] = [{ threshold: 'count>=0', abortOnFail: false }]
  options.thresholds[`http_req_failed{nombre:${nombre}}`] = [{ threshold: 'rate>=0', abortOnFail: false }]
}
