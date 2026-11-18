# Historial de pruebas de estrés de b-sheet

Una fila por corrida de `./estres/correr.sh`. Tiempos en milisegundos; errores = peticiones fallidas
(HTTP ≥ 400 o sin respuesta). «Umbrales» = p95 < 800 ms, login p95 < 1500 ms y errores < 1 %.
El detalle por endpoint de cada corrida está en `estres/resultados/<id>.md`.

| Corrida | Escenario | URL | Commit | Análisis en BD | VUs máx | Peticiones | Pet./s | p50 | p95 | p99 | Máx | Errores % | Umbrales |
|---|---|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| [2026-11-18_172046_humo](2026-11-18_172046_humo.md) | humo | http://localhost:8085 | `b9b4926` | 6 | 1 | 83 | 2.5 | 8 | 15 | 68 | 81 | 0 | sí |
| [2026-11-18_172126_carga](2026-11-18_172126_carga.md) | carga | http://localhost:8085 | `b9b4926` | 20006 | 20 | 3548 | 20.2 | 25 | 2057 | 2837 | 3785 | 0 | no |
| [2026-11-18_172514_carga](2026-11-18_172514_carga.md) | carga | http://localhost:8085 | `b9b4926` | 20006 | 20 | 5390 | 30.7 | 5 | 827 | 1600 | 3773 | 0 | no |
| [2026-11-18_172835_carga](2026-11-18_172835_carga.md) | carga | http://localhost:8085 | `b9b4926` | 20006 | 20 | 6583 | 37.7 | 5 | 23 | 31 | 157 | 0 | sí |
| [2026-11-18_173153_estres](2026-11-18_173153_estres.md) | estres | http://localhost:8085 | `b9b4926` | 20006 | 150 | 66071 | 95.6 | 3 | 25 | 256 | 106757 | 0 | sí |
| [2026-11-18_174325_pico](2026-11-18_174325_pico.md) | pico | http://localhost:8085 | `b9b4926` | 20006 | 200 | 20209 | 174.8 | 2 | 15 | 61 | 311 | 0 | sí |
| [2026-11-18_174522_login](2026-11-18_174522_login.md) | login | http://localhost:8085 | `b9b4926` | 20006 | 20 | 1207 | 20 | 73 | 208 | 465 | 657 | 0 | sí |
