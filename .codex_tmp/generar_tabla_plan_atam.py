from pathlib import Path
from PIL import Image, ImageDraw, ImageFont


SALIDA = Path(r"C:\Users\ALEX\AndroidStudioProjects\AplicaciónYalazas\Plan_de_accion_ATAM_Yalazas.png")
FUENTE = Path(r"C:\Windows\Fonts\segoeui.ttf")
NEGRITA = Path(r"C:\Windows\Fonts\segoeuib.ttf")

ANCHO = 2440
MARGEN = 72
ANCHO_TABLA = ANCHO - 2 * MARGEN
COLUMNAS = [310, 950, 370, 666]
assert sum(COLUMNAS) == ANCHO_TABLA

TITULO = "Plan de acción para revisar la arquitectura de Yalazas"
SUBTITULO = "Evaluación ATAM en 3 meses  ·  12 semanas"
CABECERAS = ["Periodo", "Trabajo principal", "Escenarios", "Entregable"]
FILAS = [
    (
        "Mes 1\nSemanas 1–2",
        "Dibujar la arquitectura actual: pantallas Android, repositorios, Firebase Auth, Firestore, reglas, seguimiento y proceso de publicación. Comprobar qué reglas están activas en Firebase.",
        "Todos",
        "Diagrama actualizado e inventario de decisiones arquitectónicas.",
    ),
    (
        "Mes 1\nSemanas 3–4",
        "Revisar la matriz con quienes usan la app. Definir para cada escenario qué debe ocurrir y cómo se medirá; por ejemplo, qué significa «actualizar con rapidez».",
        "Todos",
        "Matriz de utilidad acordada, con prioridades y medidas de éxito.",
    ),
    (
        "Mes 2\nSemanas 5–6",
        "Probar cancelación en cocina, acceso por rol y cortes de internet al confirmar pedidos o entregas. Usar cuentas de prueba y el emulador de Firestore sin afectar pedidos reales.",
        "Seguridad y disponibilidad",
        "Resultados de pruebas, fallos reproducibles y riesgos confirmados.",
    ),
    (
        "Mes 2\nSemanas 7–8",
        "Simular usuarios y pedidos simultáneos. Medir actualización del seguimiento, apertura de pantallas, lecturas de Firestore y comportamiento del teléfono. Repetir con mayor carga.",
        "Rendimiento y escalabilidad",
        "Línea base de mediciones y puntos donde aparecen demoras o exceso de lecturas.",
    ),
    (
        "Mes 3\nSemanas 9–10",
        "Evaluar dos cambios de ejemplo: modificar la tarifa de delivery y diseñar cómo se conectaría un panel web administrativo. Identificar lógica reutilizable y dependencias de Android.",
        "Mantenibilidad; relación con seguridad y rendimiento",
        "Matriz de riesgos, puntos de sensibilidad y alternativas de arquitectura.",
    ),
    (
        "Mes 3\nSemanas 11–12",
        "Ordenar riesgos por impacto y esfuerzo. Elegir mejoras inmediatas, posteriores y las que necesitan más información. Revisar conclusiones con los responsables del restaurante.",
        "Todos",
        "Informe ATAM final y plan priorizado de implementación.",
    ),
]


def fuente(ruta, tam):
    return ImageFont.truetype(str(ruta), tam)


f_titulo = fuente(NEGRITA, 66)
f_subtitulo = fuente(FUENTE, 37)
f_cabecera = fuente(NEGRITA, 36)
f_cuerpo = fuente(FUENTE, 33)
f_periodo = fuente(NEGRITA, 36)


def ancho_texto(draw, texto, font):
    return draw.textbbox((0, 0), texto, font=font)[2]


def envolver(draw, texto, font, ancho):
    lineas = []
    for parrafo in texto.split("\n"):
        actual = ""
        for palabra in parrafo.split():
            prueba = palabra if not actual else f"{actual} {palabra}"
            if ancho_texto(draw, prueba, font) <= ancho or not actual:
                actual = prueba
            else:
                lineas.append(actual)
                actual = palabra
        lineas.append(actual)
    return lineas


provisional = Image.new("RGB", (ANCHO, 200), "white")
d = ImageDraw.Draw(provisional)
PADDING_X = 25
PADDING_Y = 27
ALTO_LINEA = 48
filas_envueltas = []
altos = []
for fila in FILAS:
    envuelta = []
    for indice, texto in enumerate(fila):
        font = f_periodo if indice == 0 else f_cuerpo
        envuelta.append(envolver(d, texto, font, COLUMNAS[indice] - PADDING_X * 2))
    filas_envueltas.append(envuelta)
    altos.append(max(122, max(len(lineas) for lineas in envuelta) * ALTO_LINEA + PADDING_Y * 2))

Y_TABLA = 235
ALTO_CABECERA = 83
ALTO = Y_TABLA + ALTO_CABECERA + sum(altos) + 95
imagen = Image.new("RGB", (ANCHO, ALTO), "#FFFFFF")
draw = ImageDraw.Draw(imagen)

draw.text((MARGEN, 56), TITULO, font=f_titulo, fill="#17364A")
draw.text((MARGEN, 145), SUBTITULO, font=f_subtitulo, fill="#53727F")
draw.line((MARGEN, 207, ANCHO - MARGEN, 207), fill="#C9D9DE", width=3)

x = MARGEN
for indice, encabezado in enumerate(CABECERAS):
    ancho = COLUMNAS[indice]
    draw.rectangle((x, Y_TABLA, x + ancho, Y_TABLA + ALTO_CABECERA), fill="#17364A")
    draw.text((x + PADDING_X, Y_TABLA + 19), encabezado, font=f_cabecera, fill="#FFFFFF")
    x += ancho

y = Y_TABLA + ALTO_CABECERA
for indice_fila, (fila, alto) in enumerate(zip(filas_envueltas, altos)):
    x = MARGEN
    for indice_columna, lineas in enumerate(fila):
        ancho = COLUMNAS[indice_columna]
        relleno = "#2E7374" if indice_columna == 0 else ("#F0F5F7" if indice_fila % 2 else "#FFFFFF")
        draw.rectangle((x, y, x + ancho, y + alto), fill=relleno, outline="#D2DEE3", width=2)
        font = f_periodo if indice_columna == 0 else f_cuerpo
        color = "#FFFFFF" if indice_columna == 0 else "#18313B"
        alto_texto = len(lineas) * ALTO_LINEA
        linea_y = y + max(PADDING_Y, (alto - alto_texto) // 2)
        for linea in lineas:
            draw.text((x + PADDING_X, linea_y), linea, font=font, fill=color)
            linea_y += ALTO_LINEA
        x += ancho
    y += alto

draw.text((MARGEN, ALTO - 55), "Yalazas  ·  Revisión de arquitectura", font=f_subtitulo, fill="#6E8890")
imagen.save(SALIDA, optimize=True)
print(f"{SALIDA} | {ANCHO} × {ALTO} px")
