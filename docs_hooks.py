"""Hook de MkDocs: los enlaces internos del README («#gestión-de-cuentas») llevan a la página del sitio
donde quedó ese encabezado.

En GitHub el README es una sola página; en el sitio cada sección es su propia página (incluida con
include-markdown), que reescribe «#ancla» como «..#ancla». El mapa ancla → página se arma con los
encabezados de cada página ya con el README incluido: no hay que mantenerlo a mano. MkDocs procesa el
Markdown de todas las páginas antes de generar el HTML, así que el mapa está completo en on_post_page.
"""
import re

from pymdownx.slugs import slugify

_slug = slugify(case="lower")  # mismo slug que GitHub y que toc (conserva acentos)
_ENCABEZADO = re.compile(r"^#{1,6}\s+(.+?)\s*#*\s*$", re.M)
_ENLACE = re.compile(r'href="\.\.#([^"]+)"')
_anclas: dict[str, str] = {}


def on_page_markdown(markdown, page, **kwargs):
    for titulo in _ENCABEZADO.findall(markdown):
        _anclas.setdefault(_slug(titulo, "-"), page.file.src_uri)
    return markdown


def on_post_page(output, page, **kwargs):
    actual = page.file.src_uri

    def reemplazar(m):
        ancla = m.group(1)
        destino = _anclas.get(ancla)
        if destino is None:
            return m.group(0)
        if destino == actual:
            return f'href="#{ancla}"'
        base = "" if actual == "index.md" else "../"
        ruta = "" if destino == "index.md" else destino[: -len(".md")] + "/"
        return f'href="{base}{ruta}#{ancla}"'

    return _ENLACE.sub(reemplazar, output)
