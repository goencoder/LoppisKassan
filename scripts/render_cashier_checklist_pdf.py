from pathlib import Path
from textwrap import wrap

from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.pdfbase.pdfmetrics import stringWidth
from reportlab.pdfgen import canvas


PAGE_WIDTH, PAGE_HEIGHT = A4
MARGIN_X = 16 * mm
TOP_MARGIN = 18 * mm
BOTTOM_MARGIN = 14 * mm
CONTENT_WIDTH = PAGE_WIDTH - (2 * MARGIN_X)


def draw_header(pdf, title, subtitle, page_no):
    y = PAGE_HEIGHT - TOP_MARGIN
    pdf.setFillColor(colors.HexColor("#111827"))
    pdf.setFont("Helvetica-Bold", 18)
    pdf.drawString(MARGIN_X, y, title)
    pdf.setFont("Helvetica", 9)
    pdf.setFillColor(colors.HexColor("#4B5563"))
    pdf.drawRightString(PAGE_WIDTH - MARGIN_X, y + 1, f"Sida {page_no} av 2")
    y -= 14
    pdf.drawString(MARGIN_X, y, subtitle)
    pdf.setStrokeColor(colors.HexColor("#D1D5DB"))
    pdf.line(MARGIN_X, y - 8, PAGE_WIDTH - MARGIN_X, y - 8)
    return y - 18


def draw_section_title(pdf, y, title):
    pdf.setFillColor(colors.HexColor("#0F172A"))
    pdf.setFont("Helvetica-Bold", 10.5)
    pdf.drawString(MARGIN_X, y, title)
    return y - 16


def draw_paragraph(pdf, y, text, font_name="Helvetica", font_size=9, leading=11, color="#111827"):
    pdf.setFont(font_name, font_size)
    pdf.setFillColor(colors.HexColor(color))
    max_width = CONTENT_WIDTH
    lines = []
    for paragraph in text.split("\n"):
        if not paragraph.strip():
            lines.append("")
            continue
        words = paragraph.split()
        current = ""
        for word in words:
            test = word if not current else current + " " + word
            if stringWidth(test, font_name, font_size) <= max_width:
                current = test
            else:
                if current:
                    lines.append(current)
                current = word
        if current:
            lines.append(current)
    for line in lines:
        if line:
            pdf.drawString(MARGIN_X, y, line)
        y -= leading
    return y


def draw_arrow_lines(pdf, y, lines):
    pdf.setFont("Helvetica", 9)
    pdf.setFillColor(colors.HexColor("#111827"))
    for line in lines:
        pdf.drawString(MARGIN_X + 3 * mm, y, f"-> {line}")
        y -= 10
    return y


def draw_box(pdf, y, lines, fill="#F8FAFC", stroke="#CBD5E1", font_size=8.3, leading=10):
    padding = 4 * mm
    box_height = padding * 2 + (len(lines) * leading)
    box_y = y - box_height
    pdf.setFillColor(colors.HexColor(fill))
    pdf.setStrokeColor(colors.HexColor(stroke))
    pdf.roundRect(MARGIN_X, box_y, CONTENT_WIDTH, box_height, 6, stroke=1, fill=1)
    pdf.setFillColor(colors.HexColor("#0F172A"))
    pdf.setFont("Courier", font_size)
    text_y = y - padding - font_size + 3
    for line in lines:
        pdf.drawString(MARGIN_X + padding, text_y, line)
        text_y -= leading
    return box_y - 6


def draw_contact_table(pdf, y):
    rows = [
        ("Roll", "Namn", "Telefon"),
        ("Loppisansvarig", "", ""),
        ("Tekniskt ansvarig", "", ""),
        ("Backup-kontakt", "", ""),
    ]
    row_height = 7.0 * mm
    col_widths = [60 * mm, 58 * mm, CONTENT_WIDTH - 118 * mm]
    top_y = y
    x = MARGIN_X
    for row_index, row in enumerate(rows):
        row_y = top_y - ((row_index + 1) * row_height)
        fill = "#E5E7EB" if row_index == 0 else "#FFFFFF"
        pdf.setStrokeColor(colors.HexColor("#CBD5E1"))
        row_x = x
        for col_index, value in enumerate(row):
            width = col_widths[col_index]
            pdf.setFillColor(colors.HexColor(fill))
            pdf.rect(row_x, row_y, width, row_height, stroke=1, fill=1)
            pdf.setFillColor(colors.HexColor("#111827"))
            pdf.setFont("Helvetica-Bold" if row_index == 0 else "Helvetica", 7.4)
            pdf.drawString(row_x + 4, row_y + row_height - 10, value)
            row_x += width
    return top_y - (len(rows) * row_height) - 2


def draw_footer(pdf):
    pdf.setStrokeColor(colors.HexColor("#E5E7EB"))
    pdf.line(MARGIN_X, BOTTOM_MARGIN + 8, PAGE_WIDTH - MARGIN_X, BOTTOM_MARGIN + 8)
    pdf.setFont("Helvetica", 8)
    pdf.setFillColor(colors.HexColor("#6B7280"))
    pdf.drawString(MARGIN_X, BOTTOM_MARGIN, "LoppisKassan - kassachecklista för utskrift")
    pdf.drawRightString(PAGE_WIDTH - MARGIN_X, BOTTOM_MARGIN, "Skriv ut dubbelsidigt och laminera")


def page_one(pdf):
    y = draw_header(
        pdf,
        "KASSACHECKLIST",
        "Sida 1: Normalt kassabeteende",
        1,
    )

    y = draw_section_title(pdf, y, "Så fungerar kassan")
    y = draw_paragraph(
        pdf,
        y,
        "Kassan sparar varje köp lokalt på datorn direkt. Uppladdning till servern sker i bakgrunden var 30:e sekund. Om nätet går ner fortsätter kassan att fungera och köp sparas lokalt.",
    ) - 3

    y = draw_section_title(pdf, y, "Statusfältet (nedre vänstra hörnet)")
    y = draw_box(
        pdf,
        y,
        [
            "[Grön prick]  Ansluten till iLoppis       = Allt OK",
            "[Gul prick]   Väntar på uppladdning (N)  = Nätproblem, men köp sparas",
            "[Röd prick]   Avvisade poster - N        = Klicka och fixa",
        ],
        fill="#F7FCEB",
        stroke="#B9D68A",
    )

    y = draw_section_title(pdf, y, "Normal arbetsordning")
    y = draw_box(
        pdf,
        y,
        [
            "1. Skriv säljnummer     ->  TAB",
            "2. Skriv pris           ->  ENTER",
            "3. Upprepa för fler varor",
            "4. Välj betalmetod (Kontant / Swish)",
            "5. Slutför köp",
        ],
    )
    y = draw_paragraph(pdf, y, "Markören hoppar automatiskt tillbaka till säljnummer efter varje köp.") - 3

    y = draw_section_title(pdf, y, "Vanliga dialogrutor (inget farligt)")
    y = draw_box(
        pdf,
        y,
        [
            '"Felaktigt säljnummer"',
            "  Du skrev fel. Skriv rätt säljnummer (bara siffror). Tryck OK.",
            "",
            '"Felaktigt pris"',
            "  Priset måste vara ett heltal (inga kommatecken). Tryck OK.",
            "",
            '"Säljare ej godkänd"',
            "  Numret finns inte i listan. Kontrollera med säljaren.",
            "",
            '"Kassakoden kunde inte verifieras"',
            "  Fel kod. Be loppisansvarig om rätt kod. Försök igen.",
        ],
        font_size=7.9,
        leading=9,
    )
    y = draw_paragraph(pdf, y, "Alla dessa är normala misstag. Korrigera och fortsätt.") - 3

    y = draw_section_title(pdf, y, "Kassakodsdialog")
    y = draw_box(
        pdf,
        y,
        [
            '"Ange kassakod för att öppna..."',
            "eller",
            '"Inloggningen är inte giltig. Ange ny kassakod."',
        ],
        fill="#FFF7ED",
        stroke="#FDBA74",
    )
    y = draw_arrow_lines(
        pdf,
        y,
        [
            "Be loppisansvarig om koden (XXX-XXX).",
            "Mata in koden. Kassan öppnar.",
            "Inga köp förloras. De finns kvar lokalt.",
        ],
    ) - 2

    y = draw_section_title(pdf, y, "Gult statusfält / Degraderat läge")
    y = draw_box(
        pdf,
        y,
        [
            '"Nätverksfel - Kassa i degraderat läge"',
            '"Väntar på uppladdning (N)"',
            '"Offline - poster väntar på synkronisering"',
        ],
        fill="#FFFBEB",
        stroke="#FCD34D",
    )
    y = draw_arrow_lines(
        pdf,
        y,
        [
            "Tryck OK om dialog visas.",
            "Fortsätt sälja som vanligt.",
            "Köp sparas lokalt och synkas automatiskt senare.",
        ],
    ) - 2

    y = draw_section_title(pdf, y, "Offlinestart")
    y = draw_box(
        pdf,
        y,
        [
            '"Kassa öppnad (offline)"',
            '"Gammal cachad data - fortsätta ändå?"',
        ],
        fill="#F8FAFC",
        stroke="#CBD5E1",
    )
    y = draw_arrow_lines(
        pdf,
        y,
        [
            "Tryck OK / Ja.",
            "Kassan fungerar men säljarlistan kan vara gammal.",
            "Fortsätt sälja.",
        ],
    )

    draw_footer(pdf)


def page_two(pdf):
    y = draw_header(
        pdf,
        "KASSACHECKLIST",
        "Sida 2: Risk / alert",
        2,
    )

    y = draw_section_title(pdf, y, "Kritiska felmeddelanden - kräver åtgärd nu")
    y = draw_box(
        pdf,
        y,
        [
            '!! "Fel vid skrivning till kassafil"        !!',
            '!! "Problem med filrättigheter"             !!',
            '!! "Kunde inte skapa kataloger"             !!',
        ],
        fill="#FEF2F2",
        stroke="#FCA5A5",
        font_size=7.8,
        leading=8.5,
    )
    y = draw_paragraph(pdf, y, "Om du ser något av dessa:") - 2
    y = draw_box(
        pdf,
        y,
        [
            "1. STOPPA kassaregistrering på denna dator",
            "2. RÖR INGENTING mer på datorn",
            "3. INFORMERA loppisansvarig omedelbart",
            "4. BYT till annan dator eller pappersläge",
        ],
        fill="#FFF1F2",
        stroke="#FDA4AF",
        font_size=7.8,
        leading=8.5,
    )

    y = draw_section_title(pdf, y, "Röda pricken - avvisade poster")
    y = draw_box(
        pdf,
        y,
        ['[Röd prick] "Avvisade poster - 2"'],
        fill="#FEF2F2",
        stroke="#FCA5A5",
    )
    y = draw_arrow_lines(
        pdf,
        y,
        [
            "Klicka på röda pricken.",
            "Dialogen visar vilka köp som nekades och varför.",
        ],
    ) - 2
    y = draw_box(
        pdf,
        y,
        [
            "Orsak: INVALID_SELLER",
            '  Klicka "Ändra", skriv rätt säljnummer, klicka "Spara"',
            "  Posten skickas automatiskt igen inom 30 sek",
            "",
            "Orsak: DUPLICATE_RECEIPT",
            "  Ignorera. Köpet finns redan registrerat.",
            "",
            "Orsak: annat",
            "  Skriv ner detaljerna. Rapportera till loppisansvarig.",
        ],
        font_size=7.5,
        leading=8.2,
    )

    y = draw_section_title(pdf, y, "När ska datorer samlas in?")
    y = draw_paragraph(pdf, y, "Samla inte in under normal drift. Grönt eller gult i statusfältet betyder att man fortsätter sälja.") - 3
    y = draw_paragraph(pdf, y, "Samla in omedelbart vid filfel. Om du såg fel vid skrivning, filrättigheter eller kataloger:") - 2
    y = draw_box(
        pdf,
        y,
        [
            "1. Stäng INTE programmet",
            "2. Kopiera hela mappen:  ~/.loppiskassan/",
            "3. Packa i zip: kassa-X-datum-tid.zip",
            "4. Ge till tekniskt ansvarig",
        ],
        fill="#FFF7ED",
        stroke="#FDBA74",
        font_size=7.6,
        leading=8.3,
    )
    y = draw_paragraph(pdf, y, "Samla in efter loppisdag (alltid):") - 2
    y = draw_box(
        pdf,
        y,
        [
            "Från VARJE kassadator:",
            "",
            "1. Skapa datafil",
            "2. Samla in datafilen",
            "3. Ge till tekniskt ansvarig",
        ],
        font_size=7.6,
        leading=8.3,
    )

    y = draw_section_title(pdf, y, "Kontaktlista")
    y = draw_contact_table(pdf, y)

    y = draw_section_title(pdf, y, "Sammanfattning")
    y = draw_box(
        pdf,
        y,
        [
            "GRÖNT/GULT = Fortsätt sälja",
            "RÖD PRICK  = Klicka och fixa avvisade poster",
            "FILFEL     = STOPPA kassan, kopiera filer, byt dator",
            "VID TVIVEL = Ring loppisansvarig",
        ],
        fill="#F8FAFC",
        stroke="#94A3B8",
        font_size=7.6,
        leading=8.3,
    )

    draw_footer(pdf)


def main():
    output_dir = Path("output/pdf")
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "kassachecklist-laminerad.pdf"

    pdf = canvas.Canvas(str(output_path), pagesize=A4)
    pdf.setTitle("Kassachecklist - laminerad")
    pdf.setAuthor("Codex")
    pdf.setSubject("Tvåsidig kassachecklista för LoppisKassan")

    page_one(pdf)
    pdf.showPage()
    page_two(pdf)
    pdf.save()

    print(output_path)


if __name__ == "__main__":
    main()
