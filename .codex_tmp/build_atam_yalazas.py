from pathlib import Path
from docx import Document
from docx.shared import Cm, Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.enum.table import WD_TABLE_ALIGNMENT, WD_CELL_VERTICAL_ALIGNMENT
from docx.enum.text import WD_BREAK
from docx.oxml import OxmlElement
from docx.oxml.ns import qn
from PIL import Image, ImageDraw, ImageFont


ROOT = Path(r"C:\Users\ALEX\AndroidStudioProjects\AplicaciónYalazas")
OUT = ROOT / "Analisis_ATAM_Yalazas.docx"
TMP = ROOT / ".codex_tmp"
TMP.mkdir(exist_ok=True)
DIAGRAM = TMP / "arquitectura_atam.png"

NAVY = "18364D"
PALE = "F2F6F8"
GRAY = "D9D9D9"
BLACK = RGBColor(0, 0, 0)


def font_path(bold=False):
    base = Path(r"C:\Windows\Fonts")
    return str(base / ("segoeuib.ttf" if bold else "segoeui.ttf"))


def make_diagram():
    img = Image.new("RGB", (1900, 560), "white")
    draw = ImageDraw.Draw(img)
    regular = ImageFont.truetype(font_path(), 29)
    strong = ImageFont.truetype(font_path(True), 30)
    small = ImageFont.truetype(font_path(), 24)

    boxes = [
        (25, 95, 295, 365, "Usuarios", ["Cliente", "Administrador", "Cocinero", "Repartidor", "Mesero"]),
        (360, 95, 690, 365, "App Android", ["Pantallas", "ViewModels", "Navegación"]),
        (755, 95, 1110, 365, "Lógica local", ["Repositorios", "State, Builder", "Strategy, Decorator"]),
        (1175, 95, 1540, 365, "Firebase SDK", ["Autenticación", "Lectura y escritura", "Sincronización"]),
        (1605, 95, 1875, 365, "Servicios", ["Auth", "Firestore", "Storage"]),
    ]
    for x1, y1, x2, y2, title, lines in boxes:
        draw.rounded_rectangle((x1, y1, x2, y2), radius=18, fill="#F2F6F8", outline="#AABCC7", width=3)
        draw.text((x1 + 22, y1 + 24), title, font=strong, fill="#18364D")
        line_y = y1 + 78
        for line in lines:
            draw.text((x1 + 22, line_y), line, font=regular, fill="#222222")
            line_y += 38
    for x in (310, 705, 1125, 1555):
        draw.line((x, 230, x + 34, 230), fill="#506A79", width=5)
        draw.polygon([(x + 34, 230), (x + 19, 220), (x + 19, 240)], fill="#506A79")

    draw.rounded_rectangle((30, 425, 890, 535), radius=13, fill="#FFFFFF", outline="#C8D4DB", width=2)
    draw.text((54, 445), "Controles: Firebase Auth y reglas de Firestore y Storage", font=small, fill="#333333")
    draw.text((54, 480), "El repositorio no confirma las reglas publicadas.", font=small, fill="#555555")
    draw.rounded_rectangle((930, 425, 1870, 535), radius=13, fill="#FFFFFF", outline="#C8D4DB", width=2)
    draw.text((954, 445), "Dependencias externas: ubicación, OSRM e imágenes por URL", font=small, fill="#333333")
    draw.text((954, 480), "Compilación y pruebas locales; CI/CD no observado.", font=small, fill="#555555")
    img.save(DIAGRAM)


def shade(cell, fill):
    tcPr = cell._tc.get_or_add_tcPr()
    shd = OxmlElement("w:shd")
    shd.set(qn("w:fill"), fill)
    tcPr.append(shd)


def borders(cell):
    tcPr = cell._tc.get_or_add_tcPr()
    bd = tcPr.first_child_found_in("w:tcBorders")
    if bd is None:
        bd = OxmlElement("w:tcBorders")
        tcPr.append(bd)
    for edge in ("top", "left", "bottom", "right"):
        el = OxmlElement(f"w:{edge}")
        el.set(qn("w:val"), "single")
        el.set(qn("w:sz"), "4")
        el.set(qn("w:color"), GRAY)
        bd.append(el)


def margins(cell, top=90, start=90, bottom=90, end=90):
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    mar = tcPr.first_child_found_in("w:tcMar")
    if mar is None:
        mar = OxmlElement("w:tcMar")
        tcPr.append(mar)
    for side, value in (("top", top), ("start", start), ("bottom", bottom), ("end", end)):
        node = OxmlElement(f"w:{side}")
        node.set(qn("w:w"), str(value))
        node.set(qn("w:type"), "dxa")
        mar.append(node)


def no_split(row):
    trPr = row._tr.get_or_add_trPr()
    trPr.append(OxmlElement("w:cantSplit"))


def repeat_header(row):
    trPr = row._tr.get_or_add_trPr()
    el = OxmlElement("w:tblHeader")
    el.set(qn("w:val"), "true")
    trPr.append(el)


def set_cell(cell, content, header=False, centered=False):
    cell.text = str(content)
    cell.vertical_alignment = WD_CELL_VERTICAL_ALIGNMENT.CENTER
    margins(cell)
    borders(cell)
    for p in cell.paragraphs:
        p.alignment = WD_ALIGN_PARAGRAPH.CENTER if centered else WD_ALIGN_PARAGRAPH.LEFT
        p.paragraph_format.space_after = Pt(0)
        p.paragraph_format.line_spacing = 1.08
        for r in p.runs:
            r.font.name = "Aptos"
            r.font.size = Pt(8.5)
            r.font.bold = header
            r.font.color.rgb = RGBColor(255, 255, 255) if header else BLACK


def table(doc, headers, rows, widths, center_cols=()):
    t = doc.add_table(rows=1, cols=len(headers))
    t.alignment = WD_TABLE_ALIGNMENT.CENTER
    t.autofit = False
    for j, width in enumerate(widths):
        t.columns[j].width = Cm(width)
    for j, h in enumerate(headers):
        c = t.rows[0].cells[j]
        c.width = Cm(widths[j])
        set_cell(c, h, header=True, centered=j in center_cols)
        shade(c, NAVY)
    repeat_header(t.rows[0])
    no_split(t.rows[0])
    for i, values in enumerate(rows):
        row = t.add_row()
        no_split(row)
        for j, value in enumerate(values):
            c = row.cells[j]
            c.width = Cm(widths[j])
            set_cell(c, value, centered=j in center_cols)
            if i % 2:
                shade(c, PALE)
    doc.add_paragraph().paragraph_format.space_after = Pt(1)
    return t


def paragraph(doc, text="", bold_prefix=None, style=None):
    p = doc.add_paragraph(style=style)
    if bold_prefix and text.startswith(bold_prefix):
        p.add_run(bold_prefix).bold = True
        p.add_run(text[len(bold_prefix):])
    else:
        p.add_run(text)
    return p


def heading(doc, text, level=1):
    return doc.add_paragraph(text, style=f"Heading {level}")


def numbered(doc, title, body):
    p = doc.add_paragraph(style="List Number")
    p.add_run(title).bold = True
    p.add_run(" " + body)


make_diagram()
doc = Document()
sec = doc.sections[0]
sec.page_width = Cm(21)
sec.page_height = Cm(29.7)
sec.top_margin = Cm(1.85)
sec.bottom_margin = Cm(1.75)
sec.left_margin = Cm(1.8)
sec.right_margin = Cm(1.8)

styles = doc.styles
normal = styles["Normal"]
normal.font.name = "Aptos"
normal.font.size = Pt(10)
normal.font.color.rgb = BLACK
normal.paragraph_format.space_after = Pt(6)
normal.paragraph_format.line_spacing = 1.13

for style_name, size, before, after in (("Title", 22, 0, 10), ("Heading 1", 14, 14, 7), ("Heading 2", 11, 10, 5)):
    st = styles[style_name]
    st.font.name = "Aptos Display" if style_name == "Title" else "Aptos"
    st.font.size = Pt(size)
    st.font.bold = True
    st.font.color.rgb = BLACK
    st.paragraph_format.space_before = Pt(before)
    st.paragraph_format.space_after = Pt(after)
    st.paragraph_format.keep_with_next = True

# Word's default Title style may carry a blue bottom rule; remove it explicitly.
title_ppr = styles["Title"]._element.get_or_add_pPr()
for child in list(title_ppr):
    if child.tag == qn("w:pBdr"):
        title_ppr.remove(child)

styles["List Number"].font.name = "Aptos"
styles["List Number"].font.size = Pt(10)
styles["List Number"].paragraph_format.space_after = Pt(7)

footer = sec.footer.paragraphs[0]
footer.alignment = WD_ALIGN_PARAGRAPH.RIGHT
footer.add_run("Yalazas  |  Análisis ATAM  |  ")
fld = OxmlElement("w:fldSimple")
fld.set(qn("w:instr"), "PAGE")
footer._p.append(fld)
for r in footer.runs:
    r.font.name = "Aptos"
    r.font.size = Pt(8)
    r.font.color.rgb = RGBColor(90, 90, 90)

doc.add_paragraph("Análisis ATAM de atributos de calidad de Yalazas", style="Title")
p = paragraph(doc, "Informe técnico  |  5 de noviembre de 2026")
p.paragraph_format.space_after = Pt(13)
for r in p.runs:
    r.font.size = Pt(9)
    r.font.color.rgb = RGBColor(95, 95, 95)

paragraph(doc, "Este informe examina seguridad, disponibilidad, rendimiento, mantenibilidad y escalabilidad de la aplicación Android Yalazas. Su objetivo es convertir la arquitectura actual en escenarios comprobables, localizar los riesgos más relevantes y establecer un orden de trabajo para mejorarla.")
paragraph(doc, "Conclusión principal: la organización por roles y repositorios ofrece una base útil para crecer. La prioridad es asegurar los importes de los pedidos y restringir los datos personales visibles antes de asignar un repartidor. Después conviene controlar las lecturas en tiempo real y verificar que las reglas probadas sean las que están publicadas.")
paragraph(doc, "Alcance: revisión del código y la configuración disponibles, incluidos cambios locales. Las metas numéricas y prioridades son propuestas para una evaluación ATAM; todavía no se han validado con responsables del restaurante ni mediante pruebas de carga o comprobación de la instancia publicada.")

heading(doc, "1 Arquitectura actual")
p = doc.add_paragraph()
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
r = p.add_run()
r.add_picture(str(DIAGRAM), width=Cm(17.3))
p.paragraph_format.space_after = Pt(4)
p = paragraph(doc, "Figura 1. Flujo principal de Yalazas y servicios de los que depende.")
p.alignment = WD_ALIGN_PARAGRAPH.CENTER
p.paragraph_format.space_after = Pt(11)
for r in p.runs:
    r.font.size = Pt(8)

table(doc, ["Área", "Situación observada"], [
    ("Frontend", "Aplicación Android en Kotlin, con pantallas por rol y navegación desde MainActivity."),
    ("Backend", "Firebase proporciona autenticación, almacenamiento y sincronización. Los repositorios Android contienen buena parte de la lógica de negocio. No se encontró servidor propio ni Cloud Functions para procesar compras."),
    ("Base de datos", "Cloud Firestore almacena users, products, promotions, orders, seguimiento y auditoría."),
    ("Infraestructura", "Firebase, ubicación del dispositivo, mapas y un servicio público de OSRM para estimar rutas."),
    ("Seguridad", "Firebase Auth y reglas por rol en firestore.rules y storage.rules. El repositorio no confirma qué versión de las reglas está publicada en Firebase."),
    ("CI y CD", "Gradle, pruebas unitarias y pruebas locales de reglas. No se observó un flujo automatizado de compilación, pruebas y despliegue. firebase.json define los archivos de reglas para un despliegue manual."),
], [3.2, 14.2])
paragraph(doc, "La separación en repositorios y los patrones ya incorporados facilitan cambios futuros. Los límites más importantes son el cálculo financiero en el cliente, las consultas amplias en tiempo real y la falta de evidencia sobre la publicación de reglas.")

heading(doc, "2 Atributos de calidad y prioridad inicial")
table(doc, ["Atributo", "Qué debe proteger", "Prioridad", "Dificultad"], [
    ("Seguridad", "Datos personales, permisos por rol e importes correctos", "Alta", "Alta"),
    ("Disponibilidad", "Toma y seguimiento de pedidos ante fallos de red o rutas", "Alta", "Media"),
    ("Rendimiento", "Apertura ágil de paneles y seguimiento fluido", "Alta", "Media"),
    ("Mantenibilidad", "Cambiar estados, reglas y funciones por rol sin romper otros flujos", "Media", "Media"),
    ("Escalabilidad", "Más pedidos, usuarios y repartidores con costos controlados", "Media", "Alta"),
], [3.25, 8.65, 2.75, 2.75], center_cols=(2, 3))
paragraph(doc, "Estas prioridades son provisionales. Para completar ATAM se deben revisar los escenarios con representantes de cliente, cocina, reparto, mesero y administración, y ajustar la importancia de cada uno.")

heading(doc, "3 Escenarios de calidad")
paragraph(doc, "Cada escenario describe un estímulo, la respuesta que debería ofrecer la arquitectura y una meta verificable. Las metas siguientes no son resultados ya medidos.")
table(doc, ["ID", "Atributo", "Escenario y respuesta esperada", "Meta propuesta"], [
    ("S1", "Seguridad", "Un cliente modifica la app y envía un pedido por menos del precio real. Un componente confiable calcula o verifica el importe y rechaza el intento.", "100 % de importes manipulados rechazados; prueba automatizada."),
    ("S2", "Seguridad", "Un repartidor no asignado consulta pedidos disponibles. Solo recibe datos mínimos para decidir; dirección y teléfono completos aparecen tras asignarlo.", "0 datos personales completos expuestos antes de la asignación."),
    ("S3", "Seguridad", "Un mesero confirma manualmente efectivo o Yape/Plin. Se registra quién confirmó y el cobro puede conciliarse.", "100 % de entregas en mesa con confirmación y registro comprobable."),
    ("A1", "Disponibilidad", "Se pierde internet al confirmar un pedido. La interfaz diferencia pendiente de envío y confirmado, y evita duplicados al reconectar.", "Estado visible en 3 s o menos; ningún pedido duplicado."),
    ("A2", "Disponibilidad", "El servicio de rutas deja de responder durante el rastreo. La pantalla conserva la ubicación y distancia disponibles y explica que falta la ruta estimada.", "Seguimiento principal utilizable; alternativa en 3 s o menos."),
    ("P1", "Rendimiento", "Administración abre ventas con 10 000 pedidos históricos. Se muestran datos resumidos y una página acotada de pedidos.", "Vista inicial en menos de 2 s; máximo 50 documentos por página."),
    ("P2", "Rendimiento", "Un repartidor lleva varios pedidos y comparte ubicación. Los clientes ven una posición reciente sin operaciones innecesarias.", "Ubicación con menos de 30 s de antigüedad; operaciones medidas."),
    ("M1", "Mantenibilidad", "Se agrega un estado de pedido. Las transiciones, permisos y pantallas se actualizan de forma coherente.", "Pruebas de transiciones y reglas por rol antes de publicar."),
    ("M2", "Mantenibilidad", "Cambia una regla de acceso del mesero. El equipo detecta si afecta cliente, cocina o reparto.", "La compilación se bloquea si falla una prueba de reglas."),
    ("E1", "Escalabilidad", "Los pedidos crecen diez veces. Consultas limitadas y paginadas evitan leer todo el historial al abrir una pantalla.", "Lecturas iniciales acotadas y costo medido antes de publicar."),
], [0.9, 3.0, 8.5, 5.0], center_cols=(0,))
paragraph(doc, "Firestore ofrece caché sin conexión en Android, pero una lectura de caché puede estar desactualizada. Por eso A1 distingue entre operación pendiente y pedido confirmado por el restaurante.")

heading(doc, "4 Riesgos detectados y tácticas")
table(doc, ["ID e impacto", "Tipo", "Descripción y evidencia", "Táctica recomendada"], [
    ("R1\nAlto", "Integridad financiera y seguridad", "RepositorioPedidos.kt prepara el pedido en el cliente. Las reglas comprueban condiciones como total mayor que cero, pero no recalculan con precios oficiales y promociones.", "Crear pedidos mediante una operación confiable que reciba productos y cantidades, calcule el importe y lo escriba. Probar solicitudes alteradas."),
    ("R2\nAlto", "Privacidad y autorización", "Las reglas permiten al repartidor leer pedidos de entrega antes de la asignación. Filtrar en la interfaz ocurre después de recibir el documento.", "Separar una vista mínima de pedidos disponibles; entregar dirección y teléfono completos solo al asignado. Probar permisos por documento."),
    ("R3\nAlto", "Despliegue y seguridad", "Hay reglas y pruebas locales, pero el repositorio no demuestra que la versión revisada esté activa en Firebase ni contiene publicación automatizada.", "Versionar y probar reglas en cada cambio; verificar proyecto de destino y versión publicada antes de liberar la app."),
    ("R4\nAlto", "Rendimiento, escalabilidad y costo", "Administración, ventas, mesero y reparto consultan conjuntos completos y filtran parte en el dispositivo. Las escuchas pueden generar lecturas facturables.", "Consultar por estado y rol, limitar, paginar, crear índices y usar agregados para ventas. Medir lecturas por pantalla."),
    ("R5\nAlto", "Fraude y operación", "El mesero confirma manualmente haber recibido o comprobado el pago; la app no verifica directamente Yape/Plin.", "Registrar medio, referencia cuando corresponda, responsable y hora; conciliar al cierre del día y controlar correcciones."),
    ("R6\nMedio", "Disponibilidad y experiencia", "Firestore puede mantener escrituras pendientes. El flujo actual no distingue de manera explícita pendiente de sincronización y recibido por el restaurante.", "Identificador único por intento, estados pendiente y confirmado, reintento seguro y pruebas con modo avión."),
    ("R7\nMedio", "Disponibilidad externa", "RepositorioRutaEstimada.kt utiliza el endpoint público de OSRM. Sus fallos o demoras afectan la ruta mostrada.", "Proveedor con capacidad acordada para producción, espera breve, alternativa visual y medición de fallos."),
    ("R8\nMedio", "Mantenibilidad y regresión", "Existen pruebas de reglas, pero no se encontraron casos específicos de mesero y pedidos de mesa ni ejecución automática en CI.", "Agregar casos de mesero, pago y transiciones; ejecutarlos con las pruebas Android antes de publicar."),
    ("R9\nMedio", "Privacidad y operación", "El manifiesto permite copias de seguridad y los archivos de extracción conservan la plantilla. No se observó instrumentación de fallos y rendimiento en la app.", "Revisar exclusiones de copia, registrar fallos y latencias, y definir alertas de pedidos y rastreo."),
], [1.8, 3.2, 6.4, 6.0], center_cols=(0,))
paragraph(doc, "Las reglas de Firestore no filtran una consulta para ocultar documentos no autorizados: la consulta y el modelo de datos deben cumplir la política de acceso. Esto refuerza R2. Las escuchas en tiempo real exigen presupuestar sus lecturas, lo que refuerza R4.")

heading(doc, "5 Sensibilidades y puntos de compromiso")
paragraph(doc, "Un punto de sensibilidad es una decisión cuyo pequeño cambio afecta mucho un atributo. Un punto de compromiso afecta varios atributos a la vez y requiere equilibrarlos.")
table(doc, ["Decisión sensible", "Atributos afectados", "Compromiso y recomendación"], [
    ("Alcance y límite de escuchas de Firestore", "Rendimiento, escalabilidad, costo y disponibilidad", "Más actualizaciones inmediatas aportan frescura, pero aumentan lecturas y trabajo del dispositivo. Reservar tiempo real para pedidos activos y paginar el historial."),
    ("Frecuencia de ubicación", "Frescura del rastreo, batería, red y costo", "Intervalos cortos muestran mejor el recorrido y consumen más recursos. Adaptar la frecuencia según movimiento y estado del pedido."),
    ("Permisos para ver repartos", "Seguridad y facilidad operativa", "El repartidor necesita datos para aceptar, pero dirección y teléfono completos deben esperar a la asignación."),
    ("Operaciones sin conexión", "Disponibilidad y consistencia", "Permitir trabajo sin red mantiene la app útil; exige mostrar información provisional y resolver reintentos sin duplicados."),
    ("Cálculo confiable del precio", "Seguridad, rendimiento y mantenibilidad", "Trasladarlo a un componente confiable añade una operación de red y trabajo de desarrollo, pero impide que el dispositivo imponga el importe final."),
], [4.1, 4.5, 8.8])

heading(doc, "6 Prioridad de trabajo recomendada")
numbered(doc, "Proteger importes y datos personales.", "Resolver R1 y R2. Cierre: pruebas con un cliente que altera el precio y con un repartidor no asignado que intenta consultar datos privados.")
numbered(doc, "Asegurar la publicación de reglas.", "Resolver R3 y R8. Incluir mesero, pedidos de mesa, pagos y transiciones en pruebas automáticas; comprobar las reglas activas en Firebase.")
numbered(doc, "Acotar las lecturas.", "Resolver R4 con consultas específicas, paginación y agregados. Comparar lecturas y tiempo de carga con historiales grandes.")
numbered(doc, "Asegurar la operación diaria.", "Resolver R5 y R6 con conciliación de pagos y estados visibles de sincronización. Probar cortes de internet durante confirmación y entrega.")
numbered(doc, "Fortalecer el rastreo y la observación.", "Resolver R7 y R9. Medir latencia, fallos, consumo de lecturas y revisar la configuración de copias de seguridad.")

heading(doc, "7 Conclusión y límites de la evaluación")
paragraph(doc, "La base funcional y la organización por roles y repositorios ya permiten evolucionar Yalazas. El riesgo más urgente es que la integridad del precio y parte de la privacidad dependan de controles que no quedan garantizados por un componente confiable. Luego, el crecimiento de las escuchas en tiempo real y la ausencia de verificación automática de reglas pueden convertir cambios pequeños en problemas de costo o acceso.")
paragraph(doc, "Este informe no acredita tiempos de respuesta, disponibilidad real ni seguridad de la instancia publicada. Para cerrar formalmente ATAM faltan la revisión con los responsables del restaurante, pruebas de carga, pruebas de pérdida de red y comprobación de las reglas activas en Firebase.")

doc.add_page_break()
heading(doc, "8 Referencias y evidencias")
paragraph(doc, "La siguiente relación indica dónde se observaron las decisiones arquitectónicas que sustentan el análisis. La revisión de archivos locales no sustituye la comprobación del entorno Firebase publicado.")
table(doc, ["Evidencia del proyecto", "Aspecto observado"], [
    ("app/build.gradle.kts; MainActivity.kt", "Módulo Android, dependencias y navegación entre roles."),
    ("ServiciosFirebase.kt; RepositorioPedidos.kt; RepositorioEstadosPedido.kt", "Acceso a Firebase, construcción y transiciones de pedidos."),
    ("RepositorioProductos.kt; AdminSalesFragment.kt", "Escuchas en tiempo real, catálogo y consultas del panel de ventas."),
    ("RepositorioUbicacionRepartidor.kt; RepositorioRutaEstimada.kt", "Actualización de ubicación y dependencia del servicio de rutas."),
    ("firestore.rules; storage.rules; firebase.json; FIREBASE_SECURITY.md", "Autorización por rol, configuración de publicación y advertencias operativas."),
    ("firebase-rules-tests/rules.test.cjs", "Cobertura local de reglas y ausencia de casos específicos de mesero."),
    ("AndroidManifest.xml; backup_rules.xml; data_extraction_rules.xml", "Permisos y configuración de copias de seguridad del dispositivo."),
], [8.0, 9.4])
heading(doc, "Fuentes metodológicas y de Firebase", level=2)
paragraph(doc, "Software Engineering Institute, Architecture Tradeoff Analysis Method: https://www.sei.cmu.edu/library/architecture-tradeoff-analysis-method-collection/")
paragraph(doc, "Software Engineering Institute, definiciones de sensibilidad y compromisos: https://resources.sei.cmu.edu/asset_files/TechnicalNote/2003_004_001_14150.pdf")
paragraph(doc, "Firebase, acceso sin conexión de Firestore: https://firebase.google.com/docs/firestore/manage-data/enable-offline")
paragraph(doc, "Firebase, reglas y consultas: https://firebase.google.com/docs/firestore/security/rules-query")
paragraph(doc, "Firebase, precios y lecturas de Firestore: https://firebase.google.com/docs/firestore/pricing")

doc.core_properties.title = "Análisis ATAM de atributos de calidad de Yalazas"
doc.core_properties.subject = "Seguridad, disponibilidad, rendimiento, mantenibilidad y escalabilidad"
doc.core_properties.keywords = "Yalazas, ATAM, arquitectura, atributos de calidad"
doc.save(OUT)
print(OUT)
