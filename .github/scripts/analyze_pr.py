import os
import sys
import requests
from google import genai
from google.genai import types

GITHUB_TOKEN = os.getenv("GITHUB_TOKEN")
REPO_NAME = os.getenv("GITHUB_REPOSITORY")
PR_NUMBER = os.getenv("PR_NUMBER")
GEMINI_API_KEY = os.getenv("GEMINI_API_KEY")
# Configurable para cambiar de modelo cuando Google retire este, sin tocar el script.
GEMINI_MODEL = os.getenv("GEMINI_MODEL") or "gemini-2.5-flash"
# Marca invisible para encontrar el comentario del bot y editarlo en vez de duplicarlo.
COMMENT_MARKER = "<!-- gemini-pr-review -->"

if not all([GITHUB_TOKEN, REPO_NAME, PR_NUMBER, GEMINI_API_KEY]):
    print("Error: Faltan variables de entorno requeridas.")
    sys.exit(1)

# Obtener el diff unificado del PR desde la API de GitHub
headers = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3.diff",
}
diff_url = f"https://api.github.com/repos/{REPO_NAME}/pulls/{PR_NUMBER}"
diff_res = requests.get(diff_url, headers=headers)

if diff_res.status_code != 200:
    print(f"Error al obtener el diff: {diff_res.status_code} - {diff_res.text}")
    sys.exit(1)

pr_diff = diff_res.text

MAX_CHARS = 25000
truncated_diff = pr_diff[:MAX_CHARS]
if len(pr_diff) > MAX_CHARS:
    truncated_diff += "\n\n... [Diff truncado por longitud excesiva] ..."

# Inicializar cliente de Gemini
client = genai.Client(api_key=GEMINI_API_KEY)

system_instruction = (
    "Eres un arquitecto de software y revisor de código senior. Tu tarea es analizar "
    "el diff de un Pull Request y entregar un desglose estructurado, técnico y accionable."
)

prompt = f"""Analiza el siguiente diff de cambios y genera un reporte en Markdown estructurado:

1.  **Resumen Ejecutivo**: Explica en 2 o 3 líneas qué cambios introduce este PR y su propósito.
2.  **Módulos / Archivos Afectados**: Resumen conciso de qué cambió en cada componente.
3. **Riesgos y Casos Borde**: Posibles regresiones, seguridad, rendimiento o manejo de errores.
4.  **Sugerencias Técnicas**: Mejoras opcionales de arquitectura, legibilidad o estándares.

Diff del Pull Request:
```diff
{truncated_diff}
```
"""

try:
    response = client.models.generate_content(
        model=GEMINI_MODEL,
        contents=prompt,
        config=types.GenerateContentConfig(
            system_instruction=system_instruction,
            temperature=0.2,
        ),
    )
    analysis_text = response.text
except Exception as e:
    print(f"Error al invocar Gemini: {e}")
    sys.exit(1)

# Publicar el análisis en el PR: un solo comentario que se actualiza en cada push
comment_url = f"https://api.github.com/repos/{REPO_NAME}/issues/{PR_NUMBER}/comments"
comment_headers = {
    "Authorization": f"Bearer {GITHUB_TOKEN}",
    "Accept": "application/vnd.github.v3+json",
}
comment_payload = {
    "body": f"{COMMENT_MARKER}\n### 🤖 Análisis Automatizado del PR (Gemini AI)\n\n{analysis_text}"
}


def find_previous_comment():
    page = 1
    while True:
        res = requests.get(comment_url, headers=comment_headers, params={"per_page": 100, "page": page})
        if res.status_code != 200:
            print(f"Error al listar comentarios: {res.status_code} - {res.text}")
            sys.exit(1)
        comments = res.json()
        for comment in comments:
            if COMMENT_MARKER in comment["body"]:
                return comment
        if len(comments) < 100:
            return None
        page += 1


previous = find_previous_comment()
if previous:
    post_res = requests.patch(previous["url"], headers=comment_headers, json=comment_payload)
    expected_status = 200
else:
    post_res = requests.post(comment_url, headers=comment_headers, json=comment_payload)
    expected_status = 201

if post_res.status_code == expected_status:
    print("Comentario actualizado en el PR." if previous else "Comentario publicado exitosamente en el PR.")
else:
    print(f"Error al publicar comentario: {post_res.status_code} - {post_res.text}")
    sys.exit(1)
