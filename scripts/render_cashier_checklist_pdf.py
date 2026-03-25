import argparse
import os
import math
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas


PAGE_WIDTH, PAGE_HEIGHT = A4
MARGIN = 14 * mm

CREAM = colors.HexColor("#FFF6DA")
SUN = colors.HexColor("#FFD54A")
RED = colors.HexColor("#E6432C")
BLUE = colors.HexColor("#1F5FBF")
SKY = colors.HexColor("#86D2F5")
GREEN = colors.HexColor("#87C26B")
INK = colors.HexColor("#142033")
PAPER = colors.HexColor("#FFFDF7")

NIGHT = colors.HexColor("#17191F")
ASH = colors.HexColor("#4A4E59")
SMOKE = colors.HexColor("#737985")
TOXIC = colors.HexColor("#B4D455")
WARN = colors.HexColor("#F04D3A")
PALE = colors.HexColor("#E6E8EE")
RUST = colors.HexColor("#A24232")


def wrap(text, font_name, font_size, width):
    words = text.split()
    lines = []
    current = ""
    for word in words:
        candidate = word if not current else f"{current} {word}"
        if stringWidth(candidate, font_name, font_size) <= width:
            current = candidate
        else:
            if current:
                lines.append(current)
            current = word
    if current:
        lines.append(current)
    return lines


def draw_centered_lines(pdf, lines, x, y, font_name, font_size, leading, fill):
    pdf.setFillColor(fill)
    pdf.setFont(font_name, font_size)
    cursor = y
    for line in lines:
        pdf.drawCentredString(x, cursor, line)
        cursor -= leading
    return cursor


def starburst_points(cx, cy, outer_r, inner_r, points):
    coords = []
    for index in range(points * 2):
        angle = math.pi / points * index - math.pi / 2
        radius = outer_r if index % 2 == 0 else inner_r
        coords.append((cx + math.cos(angle) * radius, cy + math.sin(angle) * radius))
    return coords


def draw_starburst(pdf, cx, cy, outer_r, inner_r, points, fill, stroke):
    coords = starburst_points(cx, cy, outer_r, inner_r, points)
    path = pdf.beginPath()
    first_x, first_y = coords[0]
    path.moveTo(first_x, first_y)
    for x, y in coords[1:]:
        path.lineTo(x, y)
    path.close()
    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(2)
    pdf.drawPath(path, stroke=1, fill=1)


def draw_fact_burst(pdf, cx, cy, title, body, fill, stroke, text_color):
    draw_starburst(pdf, cx, cy, 27 * mm, 18 * mm, 11, fill, stroke)
    lines = wrap(body, "Helvetica-Bold", 9.2, 38 * mm)
    pdf.setFillColor(text_color)
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawCentredString(cx, cy + 7, title)
    pdf.setFont("Helvetica", 8.6)
    cursor = cy - 3
    for line in lines[:3]:
        pdf.drawCentredString(cx, cursor, line)
        cursor -= 10


def draw_badge(pdf, cx, cy, outer_r, inner_r, title, body):
    draw_starburst(pdf, cx - 2.5 * mm, cy - 1.5 * mm, outer_r, inner_r, 12, SKY, SKY)
    draw_starburst(pdf, cx, cy, outer_r, inner_r, 12, SUN, RED)
    pdf.setFillColor(RED)
    pdf.setFont("Helvetica-Bold", 10.5)
    pdf.drawCentredString(cx, cy + 7, title)
    pdf.setFont("Helvetica-Bold", 9.5)
    pdf.drawCentredString(cx, cy - 6, body)


def draw_ticket(pdf, x, y, w, h, fill, stroke, title, body, angle=0, dark=False):
    pdf.saveState()
    pdf.translate(x + w / 2, y + h / 2)
    pdf.rotate(angle)
    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(2)
    pdf.roundRect(-w / 2, -h / 2, w, h, 8, stroke=1, fill=1)

    notch = 5
    pdf.setFillColor(colors.white if not dark else NIGHT)
    pdf.circle(-w / 2, 0, notch, stroke=0, fill=1)
    pdf.circle(w / 2, 0, notch, stroke=0, fill=1)

    pdf.setFillColor(INK if not dark else PALE)
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawString(-w / 2 + 11, h / 2 - 18, title)
    pdf.setFont("Helvetica", 8.7)
    cursor = h / 2 - 31
    for line in wrap(body, "Helvetica", 8.7, w - 22):
        pdf.drawString(-w / 2 + 11, cursor, line)
        cursor -= 10
    pdf.restoreState()


def draw_note(
    pdf,
    x,
    y,
    w,
    h,
    title,
    lines,
    fill,
    stroke,
    text_color=INK,
    title_color=None,
    mono=False,
    angle=0,
    font_size=8.2,
    leading=9.2,
):
    pdf.saveState()
    pdf.translate(x + w / 2, y + h / 2)
    pdf.rotate(angle)

    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(2)
    pdf.roundRect(-w / 2, -h / 2, w, h, 10, stroke=1, fill=1)

    body_font = "Courier" if mono else "Helvetica"
    title_fill = title_color or text_color
    cursor = h / 2 - 16

    pdf.setFillColor(title_fill)
    pdf.setFont("Helvetica-Bold", 11.5)
    pdf.drawString(-w / 2 + 10, cursor, title)
    cursor -= 14

    pdf.setFillColor(text_color)
    pdf.setFont(body_font, font_size)
    for raw_line in lines:
        if raw_line == "":
            cursor -= leading * 0.7
            continue
        prefix = ""
        text = raw_line
        if raw_line.startswith("-> "):
            prefix = "-> "
            text = raw_line[3:]
        available_width = w - 20 - stringWidth(prefix, body_font, font_size)
        for index, line in enumerate(wrap(text, body_font, font_size, available_width)):
            draw_text = f"{prefix}{line}" if index == 0 else ("   " + line if prefix else line)
            pdf.drawString(-w / 2 + 10, cursor, draw_text)
            cursor -= leading

    pdf.restoreState()


def draw_status_board(pdf, x, y, w, h, dark=False):
    fill = colors.HexColor("#FFF5A7") if not dark else colors.HexColor("#2A2D33")
    stroke = RED if not dark else TOXIC
    text_color = INK if not dark else PALE

    pdf.setFillColor(fill)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(2)
    pdf.roundRect(x, y, w, h, 10, stroke=1, fill=1)

    pdf.setFillColor(text_color)
    pdf.setFont("Helvetica-Bold", 11.5)
    pdf.drawString(x + 10, y + h - 16, "Statusfältet (nedre vänstra hörnet)")

    rows = [
        (colors.HexColor("#4CAF50"), "[Grön prick]  Ansluten till iLoppis", "= Allt OK"),
        (colors.HexColor("#F0C43C"), "[Gul prick]   Väntar på uppladdning (N)", "= Nätproblem, men köp sparas"),
        (colors.HexColor("#E0453B"), "[Röd prick]   Avvisade poster - N", "= Klicka och fixa"),
    ]
    row_y = y + h - 33
    for dot, left_text, right_text in rows:
        pdf.setFillColor(dot)
        pdf.circle(x + 14, row_y + 2, 4, stroke=0, fill=1)
        pdf.setFillColor(text_color)
        pdf.setFont("Helvetica-Bold", 8.2)
        pdf.drawString(x + 24, row_y, left_text)
        pdf.setFont("Helvetica", 8.2)
        pdf.drawRightString(x + w - 10, row_y, right_text)
        row_y -= 12


def draw_rejected_board(pdf, x, y, w, h):
    pdf.setFillColor(colors.HexColor("#2B313A"))
    pdf.setStrokeColor(WARN)
    pdf.setLineWidth(2)
    pdf.roundRect(x, y, w, h, 10, stroke=1, fill=1)

    pdf.setFillColor(PALE)
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawString(x + 10, y + h - 16, "Röda pricken - Avvisade poster")

    pdf.setFillColor(WARN)
    pdf.circle(x + 16, y + h - 34, 5, stroke=0, fill=1)
    pdf.setFillColor(PALE)
    pdf.setFont("Courier", 8)
    pdf.drawString(x + 28, y + h - 37, '[Röd prick] "Avvisade poster - 2"')
    pdf.setFont("Helvetica", 8)
    pdf.drawString(x + 10, y + h - 50, "-> Klicka på röda pricken.")
    pdf.drawString(x + 10, y + h - 60, "-> Dialogen visar vilka köp som nekades och varför.")

    col_split = x + 48 * mm
    pdf.setStrokeColor(colors.HexColor("#59606E"))
    pdf.line(col_split, y + 10, col_split, y + h - 68)

    reasons = [
        ("INVALID_SELLER", 'Klicka "Ändra", skriv rätt säljnummer, klicka "Spara".'),
        ("DUPLICATE_RECEIPT", "Ignorera. Köpet finns redan registrerat."),
        ("annat", "Skriv ner detaljerna. Rapportera till loppisansvarig."),
    ]
    row_y = y + h - 82
    for reason, action in reasons:
        pdf.setFillColor(TOXIC if reason == "INVALID_SELLER" else WARN if reason == "annat" else PALE)
        pdf.setFont("Helvetica-Bold", 8)
        pdf.drawString(x + 10, row_y, reason)
        pdf.setFillColor(PALE)
        pdf.setFont("Helvetica", 7.7)
        for line in wrap(action, "Helvetica", 7.7, w - 48 * mm - 20):
            pdf.drawString(col_split + 8, row_y, line)
            row_y -= 8.5
        if reason == "INVALID_SELLER":
            pdf.drawString(col_split + 8, row_y, "Posten skickas automatiskt igen inom 30 sek.")
            row_y -= 8.5
        row_y -= 4


def draw_contact_table(pdf, x, y, w, h):
    pdf.setFillColor(colors.HexColor("#252A31"))
    pdf.setStrokeColor(colors.HexColor("#798092"))
    pdf.setLineWidth(2)
    pdf.roundRect(x, y, w, h, 10, stroke=1, fill=1)

    pdf.setFillColor(PALE)
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawString(x + 10, y + h - 16, "Kontaktlista")

    top = y + h - 28
    row_h = 12
    col1 = x + 10
    col2 = x + 42 * mm
    col3 = x + 67 * mm

    pdf.setFont("Helvetica-Bold", 7.8)
    pdf.drawString(col1, top, "Roll")
    pdf.drawString(col2, top, "Namn")
    pdf.drawString(col3, top, "Telefon")
    pdf.setStrokeColor(colors.HexColor("#666D7A"))
    pdf.line(x + 10, top - 4, x + w - 10, top - 4)

    rows = ["Loppisansvarig", "Tekniskt ansvarig", "Backup-kontakt"]
    cursor = top - 15
    pdf.setFont("Helvetica", 7.7)
    for row in rows:
        pdf.drawString(col1, cursor, row)
        pdf.line(x + 10, cursor - 4, x + w - 10, cursor - 4)
        cursor -= row_h


def draw_stripes(pdf, top_color, bottom_color):
    stripe_h = PAGE_HEIGHT / 10
    for index in range(10):
        pdf.setFillColor(top_color if index % 2 == 0 else bottom_color)
        pdf.rect(0, PAGE_HEIGHT - ((index + 1) * stripe_h), PAGE_WIDTH, stripe_h, stroke=0, fill=1)


def draw_happy_background(pdf):
    draw_stripes(pdf, CREAM, PAPER)
    pdf.setFillColor(SKY)
    pdf.circle(PAGE_WIDTH - 43 * mm, PAGE_HEIGHT - 35 * mm, 20 * mm, stroke=0, fill=1)
    pdf.setFillColor(SUN)
    pdf.circle(PAGE_WIDTH - 43 * mm, PAGE_HEIGHT - 35 * mm, 15 * mm, stroke=0, fill=1)

    pdf.setStrokeColor(SUN)
    pdf.setLineWidth(3)
    sun_x = PAGE_WIDTH - 43 * mm
    sun_y = PAGE_HEIGHT - 35 * mm
    for index in range(12):
        angle = math.pi * 2 * index / 12
        inner = 19 * mm
        outer = 24 * mm
        pdf.line(
            sun_x + math.cos(angle) * inner,
            sun_y + math.sin(angle) * inner,
            sun_x + math.cos(angle) * outer,
            sun_y + math.sin(angle) * outer,
        )

    pdf.setFillColor(colors.white)
    for cx, cy in [(67 * mm, PAGE_HEIGHT - 31 * mm), (105 * mm, PAGE_HEIGHT - 27 * mm)]:
        pdf.circle(cx, cy, 8 * mm, stroke=0, fill=1)
        pdf.circle(cx + 9 * mm, cy + 3 * mm, 7 * mm, stroke=0, fill=1)
        pdf.circle(cx + 18 * mm, cy, 8.5 * mm, stroke=0, fill=1)
        pdf.roundRect(cx - 2 * mm, cy - 6 * mm, 25 * mm, 7 * mm, 3, stroke=0, fill=1)


def draw_smiling_cone(pdf, x, y, scale=1.0):
    pdf.setFillColor(colors.HexColor("#F7DDEB"))
    pdf.circle(x, y + 21 * mm * scale, 11 * mm * scale, stroke=0, fill=1)
    pdf.setFillColor(colors.HexColor("#FFF0A4"))
    pdf.circle(x - 8 * mm * scale, y + 18 * mm * scale, 8.5 * mm * scale, stroke=0, fill=1)
    pdf.circle(x + 8 * mm * scale, y + 18 * mm * scale, 8.5 * mm * scale, stroke=0, fill=1)

    path = pdf.beginPath()
    path.moveTo(x - 11 * mm * scale, y + 7 * mm * scale)
    path.lineTo(x + 11 * mm * scale, y + 7 * mm * scale)
    path.lineTo(x, y - 23 * mm * scale)
    path.close()
    pdf.setFillColor(colors.HexColor("#D9A15D"))
    pdf.setStrokeColor(colors.HexColor("#B97B34"))
    pdf.setLineWidth(1.6)
    pdf.drawPath(path, stroke=1, fill=1)

    pdf.setStrokeColor(colors.HexColor("#C4873D"))
    pdf.setLineWidth(1)
    for offset in range(-8, 9, 4):
        pdf.line(x - 8 * mm * scale, y - 3 * mm * scale + offset, x + 7 * mm * scale, y - 19 * mm * scale + offset)
        pdf.line(x - 7 * mm * scale, y - 19 * mm * scale + offset, x + 8 * mm * scale, y - 3 * mm * scale + offset)

    pdf.setFillColor(INK)
    pdf.circle(x - 4.5 * mm * scale, y + 19.5 * mm * scale, 1.6 * mm * scale, stroke=0, fill=1)
    pdf.circle(x + 4.5 * mm * scale, y + 19.5 * mm * scale, 1.6 * mm * scale, stroke=0, fill=1)
    pdf.setStrokeColor(INK)
    pdf.setLineWidth(2)
    pdf.arc(x - 8 * mm * scale, y + 11 * mm * scale, x + 8 * mm * scale, y + 20 * mm * scale, startAng=200, extent=140)


def draw_ice_cream_van(pdf, x, y):
    pdf.setFillColor(RED)
    pdf.roundRect(x, y, 66 * mm, 25 * mm, 7, stroke=0, fill=1)
    pdf.setFillColor(colors.white)
    pdf.roundRect(x + 12 * mm, y + 21 * mm, 30 * mm, 5 * mm, 2.5, stroke=0, fill=1)
    pdf.setFillColor(RED)
    pdf.setFont("Helvetica-Bold", 8.5)
    pdf.drawCentredString(x + 27 * mm, y + 22.5 * mm, "GLASSBIL")

    pdf.setFillColor(colors.white)
    pdf.roundRect(x + 12 * mm, y + 11 * mm, 16 * mm, 9 * mm, 1.5, stroke=0, fill=1)
    pdf.roundRect(x + 31 * mm, y + 11 * mm, 13 * mm, 9 * mm, 1.5, stroke=0, fill=1)
    pdf.setFillColor(SUN)
    pdf.roundRect(x + 47 * mm, y + 12 * mm, 10 * mm, 7 * mm, 1.5, stroke=0, fill=1)

    pdf.setFillColor(BLUE)
    pdf.roundRect(x + 5 * mm, y + 5 * mm, 56 * mm, 3.5 * mm, 1.5, stroke=0, fill=1)
    for offset in range(6):
        pdf.setFillColor(colors.white if offset % 2 == 0 else SKY)
        pdf.rect(x + 6 * mm + offset * 9 * mm, y + 5 * mm, 5.5 * mm, 3.5 * mm, stroke=0, fill=1)

    pdf.setFillColor(INK)
    pdf.circle(x + 15 * mm, y, 5 * mm, stroke=0, fill=1)
    pdf.circle(x + 49 * mm, y, 5 * mm, stroke=0, fill=1)
    pdf.setStrokeColor(colors.white)
    pdf.setLineWidth(1.5)
    pdf.line(x + 8 * mm, y + 18 * mm, x + 10 * mm, y + 22 * mm)
    pdf.line(x + 11 * mm, y + 18 * mm, x + 13 * mm, y + 22 * mm)

    pdf.setFillColor(colors.white)
    pdf.setFont("Helvetica-Bold", 10)
    pdf.drawString(x + 8 * mm, y + 16 * mm, "GLASS")
    pdf.setFont("Helvetica-Bold", 8)
    pdf.drawString(x + 49 * mm, y + 14.5 * mm, "EL")


def draw_atom(pdf, x, y, scale=1.0, stroke=BLUE):
    pdf.saveState()
    pdf.translate(x, y)
    pdf.setStrokeColor(stroke)
    pdf.setLineWidth(2.2)
    for angle in (0, 60, 120):
        pdf.saveState()
        pdf.rotate(angle)
        pdf.ellipse(-24 * mm * scale, -8 * mm * scale, 24 * mm * scale, 8 * mm * scale, stroke=1, fill=0)
        pdf.restoreState()
    pdf.setFillColor(RED)
    pdf.circle(0, 0, 3.5 * mm * scale, stroke=0, fill=1)
    pdf.setFillColor(stroke)
    for dot_x, dot_y in [(-16 * mm * scale, 0), (9 * mm * scale, 12 * mm * scale), (11 * mm * scale, -11 * mm * scale)]:
        pdf.circle(dot_x, dot_y, 1.5 * mm * scale, stroke=0, fill=1)
    pdf.restoreState()


def draw_happy_page(pdf):
    draw_happy_background(pdf)

    pdf.setFillColor(RED)
    pdf.setFont("Helvetica-Bold", 31)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 24 * mm, "KASSACHECKLIST")
    pdf.setFont("Helvetica-Bold", 16)
    pdf.setFillColor(BLUE)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 34 * mm, "Sida 1: Normalt kassabeteende")

    pdf.setFillColor(INK)
    pdf.setFont("Helvetica-Bold", 18)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 62 * mm, "Så fungerar kassan")
    pdf.setFont("Helvetica", 9.4)
    intro = "Kassan sparar varje köp lokalt på datorn direkt. Uppladdning till servern sker i bakgrunden var 30:e sekund. Om nätet går ner fortsätter kassan att fungera - köp sparas lokalt."
    cursor = PAGE_HEIGHT - 69 * mm
    for line in wrap(intro, "Helvetica", 9.4, PAGE_WIDTH - (2 * MARGIN)):
        pdf.drawString(MARGIN, cursor, line)
        cursor -= 11

    draw_status_board(pdf, MARGIN, 131 * mm, PAGE_WIDTH - (2 * MARGIN), 30 * mm)

    draw_note(
        pdf,
        MARGIN,
        79 * mm,
        78 * mm,
        46 * mm,
        "Normal arbetsordning",
        [
            "1. Skriv säljnummer -> TAB",
            "2. Skriv pris -> ENTER",
            "3. Upprepa för fler varor",
            "4. Välj betalmetod (Kontant / Swish)",
            "5. Slutför köp",
            "",
            "Markören hoppar automatiskt tillbaka till säljnummer efter varje köp.",
        ],
        colors.HexColor("#FFF1A8"),
        RED,
        font_size=7.8,
        leading=8.5,
        angle=-3,
    )

    draw_note(
        pdf,
        98 * mm,
        71 * mm,
        98 * mm,
        54 * mm,
        "Vanliga dialogrutor (inget farligt)",
        [
            '"Felaktigt säljnummer"',
            "-> Du skrev fel. Skriv rätt säljnummer (bara siffror). Tryck OK.",
            "",
            '"Felaktigt pris"',
            "-> Priset måste vara ett heltal (inga kommatecken). Tryck OK.",
            "",
            '"Säljare ej godkänd"',
            "-> Numret finns inte i listan. Kontrollera med säljaren.",
            "",
            '"Kassakoden kunde inte verifieras"',
            "-> Fel kod. Be loppisansvarig om rätt kod. Försök igen.",
            "",
            "Alla dessa är normala misstag. Korrigera och fortsätt.",
        ],
        colors.HexColor("#F2FBFF"),
        BLUE,
        font_size=7.3,
        leading=7.8,
        angle=2,
    )

    draw_note(
        pdf,
        MARGIN,
        23 * mm,
        82 * mm,
        42 * mm,
        "Kassakodsdialog",
        [
            '"Ange kassakod för att öppna..."',
            "eller",
            '"Inloggningen är inte giltig. Ange ny kassakod."',
            "",
            "-> Be loppisansvarig om koden (XXX-XXX).",
            "-> Mata in koden. Kassan öppnar.",
            "-> Inga köp förloras - de finns kvar lokalt.",
        ],
        colors.HexColor("#FFF2C7"),
        RED,
        font_size=7.5,
        leading=8.1,
        angle=-2,
    )

    draw_note(
        pdf,
        101 * mm,
        19 * mm,
        95 * mm,
        46 * mm,
        "Gult statusfält / degraderat läge",
        [
            '"Nätverksfel - Kassa i degraderat läge"',
            '"Väntar på uppladdning (N)"',
            '"Offline - poster väntar på synkronisering"',
            "",
            "-> Tryck OK om dialog visas.",
            "-> Fortsätt sälja som vanligt.",
            "-> Köp sparas lokalt och synkas automatiskt senare.",
        ],
        colors.HexColor("#FFF6C8"),
        colors.HexColor("#E7A432"),
        font_size=7.2,
        leading=7.8,
        angle=2,
    )

    draw_note(
        pdf,
        MARGIN,
        2 * mm,
        PAGE_WIDTH - (2 * MARGIN),
        18 * mm,
        "Offlinestart",
        [
            '"Kassa öppnad (offline)"  "Gammal cachad data - fortsätta ändå?"',
            "-> Tryck OK / Ja. Kassan fungerar men säljarlistan KAN vara gammal. Fortsätt sälja.",
        ],
        colors.HexColor("#EEF8FF"),
        BLUE,
        font_size=7.4,
        leading=8.0,
    )

    pdf.setFillColor(RED)
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawRightString(PAGE_WIDTH - MARGIN, 8 * mm, "Sida 1")


def draw_dark_background(pdf):
    pdf.setFillColor(NIGHT)
    pdf.rect(0, 0, PAGE_WIDTH, PAGE_HEIGHT, stroke=0, fill=1)

    pdf.setFillColor(ASH)
    for y in range(0, int(PAGE_HEIGHT), int(24 * mm)):
        pdf.rect(0, y, PAGE_WIDTH, 10, stroke=0, fill=1)

    pdf.setFillColor(colors.HexColor("#2B2F37"))
    pdf.circle(50 * mm, PAGE_HEIGHT - 48 * mm, 20 * mm, stroke=0, fill=1)
    pdf.circle(70 * mm, PAGE_HEIGHT - 44 * mm, 15 * mm, stroke=0, fill=1)
    pdf.circle(32 * mm, PAGE_HEIGHT - 44 * mm, 14 * mm, stroke=0, fill=1)

    pdf.setFillColor(SMOKE)
    for cx, cy, r in [
        (145 * mm, PAGE_HEIGHT - 66 * mm, 18 * mm),
        (166 * mm, PAGE_HEIGHT - 59 * mm, 14 * mm),
        (128 * mm, PAGE_HEIGHT - 60 * mm, 12 * mm),
    ]:
        pdf.circle(cx, cy, r, stroke=0, fill=1)

    pdf.setFillColor(colors.HexColor("#101216"))
    pdf.rect(0, 0, PAGE_WIDTH, 22 * mm, stroke=0, fill=1)


def draw_radiation(pdf, x, y, scale=1.0):
    r = 14 * mm * scale
    pdf.setFillColor(TOXIC)
    pdf.circle(x, y, r, stroke=0, fill=1)
    pdf.setFillColor(NIGHT)
    pdf.circle(x, y, 2.5 * mm * scale, stroke=0, fill=1)

    for start in (90, 210, 330):
        path = pdf.beginPath()
        path.moveTo(x, y)
        path.arcTo(x - r, y - r, x + r, y + r, start, 45)
        path.lineTo(x, y)
        path.close()
        pdf.drawPath(path, stroke=0, fill=1)


def draw_dead_sun(pdf, x, y):
    pdf.setFillColor(colors.HexColor("#5C6270"))
    pdf.circle(x, y, 16 * mm, stroke=0, fill=1)
    pdf.setStrokeColor(colors.HexColor("#858B98"))
    pdf.setLineWidth(3)
    for index in range(10):
        angle = math.pi * 2 * index / 10
        pdf.line(
            x + math.cos(angle) * 19 * mm,
            y + math.sin(angle) * 19 * mm,
            x + math.cos(angle) * 24 * mm,
            y + math.sin(angle) * 24 * mm,
        )


def draw_smoke_cluster(pdf, x, y, scale=1.0):
    pdf.setFillColor(colors.HexColor("#8B919E"))
    for cx, cy, r in [
        (0, 0, 14),
        (16, 2, 12),
        (28, -3, 10),
        (18, -16, 14),
    ]:
        pdf.circle(x + cx * mm * scale, y + cy * mm * scale, r * mm * scale, stroke=0, fill=1)
    pdf.roundRect(x - 10 * mm * scale, y - 18 * mm * scale, 42 * mm * scale, 12 * mm * scale, 5, stroke=0, fill=1)


def draw_dark_page(pdf):
    draw_dark_background(pdf)

    pdf.setFillColor(PALE)
    pdf.setFont("Helvetica-Bold", 31)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 24 * mm, "RISK / ALERT")
    pdf.setFont("Helvetica-Bold", 15)
    pdf.setFillColor(TOXIC)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 34 * mm, "Sida 2: Kritiska fel och åtgärder")

    pdf.setFillColor(PALE)
    pdf.setFont("Helvetica-Bold", 18)
    pdf.drawString(MARGIN, PAGE_HEIGHT - 48 * mm, "Kritiska felmeddelanden - kräver åtgärd NU")

    draw_note(
        pdf,
        MARGIN,
        157 * mm,
        78 * mm,
        38 * mm,
        "Filfel",
        [
            '!! "Fel vid skrivning till kassafil" !!',
            '!! "Problem med filrättigheter" !!',
            '!! "Kunde inte skapa kataloger" !!',
        ],
        colors.HexColor("#2C2022"),
        WARN,
        text_color=PALE,
        title_color=colors.HexColor("#FFD0CA"),
        mono=True,
        angle=-2,
        font_size=7.3,
        leading=8.2,
    )

    draw_note(
        pdf,
        103 * mm,
        155 * mm,
        93 * mm,
        40 * mm,
        "Om du ser NÅGOT av dessa",
        [
            "1. STOPPA kassaregistrering på denna dator",
            "2. RÖR INGENTING mer på datorn",
            "3. INFORMERA loppisansvarig omedelbart",
            "4. BYT till annan dator eller pappersläge",
        ],
        colors.HexColor("#272C33"),
        TOXIC,
        text_color=PALE,
        title_color=TOXIC,
        angle=2,
        font_size=7.6,
        leading=8.5,
    )

    draw_rejected_board(pdf, MARGIN, 93 * mm, PAGE_WIDTH - (2 * MARGIN), 52 * mm)

    draw_note(
        pdf,
        MARGIN,
        10 * mm,
        92 * mm,
        76 * mm,
        "När ska datorer samlas in?",
        [
            "Samla INTE in under normal drift",
            "Grönt eller gult i statusfältet = fortsätt sälja.",
            "",
            "Samla in OMEDELBART vid filfel",
            'Om du såg "Fel vid skrivning" / "filrättigheter" / "kataloger":',
            "1. Stäng INTE programmet",
            "2. Kopiera hela mappen: ~/.loppiskassan/",
            "3. Packa i zip: kassa-X-datum-tid.zip",
            "4. Ge till tekniskt ansvarig",
            "",
            "Samla in EFTER loppisdag (alltid)",
            "Från VARJE kassadator:",
            "1. Skapa datafil",
            "2. Samla in datafilen",
            "3. Ge till tekniskt ansvarig",
        ],
        colors.HexColor("#23272E"),
        WARN,
        text_color=PALE,
        title_color=colors.HexColor("#FFD0CA"),
        font_size=7.0,
        leading=7.5,
        angle=-2,
    )

    draw_contact_table(pdf, 112 * mm, 43 * mm, 84 * mm, 43 * mm)

    draw_note(
        pdf,
        112 * mm,
        9 * mm,
        84 * mm,
        27 * mm,
        "Sammanfattning",
        [
            "GRÖNT/GULT = Fortsätt sälja",
            "RÖD PRICK = Klicka och fixa avvisade poster",
            "FILFEL = STOPPA kassan, kopiera filer, byt dator",
            "VID TVIVEL = Ring loppisansvarig",
        ],
        colors.HexColor("#2B1F1F"),
        TOXIC,
        text_color=PALE,
        title_color=TOXIC,
        font_size=7.1,
        leading=7.6,
        angle=1,
    )

    pdf.setFillColor(PALE)
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawRightString(PAGE_WIDTH - MARGIN, 8 * mm, "Sida 2")


def main():
    parser = argparse.ArgumentParser(description="Render the laminated cashier checklist PDF.")
    parser.add_argument(
        "--output",
        default=os.environ.get("KASSACHECKLIST_OUTPUT", "output/pdf/kassachecklist-laminerad.pdf"),
        help="Output PDF path. Defaults to output/pdf/kassachecklist-laminerad.pdf or KASSACHECKLIST_OUTPUT.",
    )
    args = parser.parse_args()

    output_path = Path(args.output)
    output_path.parent.mkdir(parents=True, exist_ok=True)

    pdf = canvas.Canvas(str(output_path), pagesize=A4)
    pdf.setTitle("Affisch - Atomdrom och efter Tjernobyl")
    pdf.setAuthor("Codex")
    pdf.setSubject("Tvasidig affisch med stark kontrast mellan glad och katastrofal sida")

    draw_happy_page(pdf)
    pdf.showPage()
    draw_dark_page(pdf)
    pdf.save()

    print(output_path)


if __name__ == "__main__":
    main()
