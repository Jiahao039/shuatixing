#!/usr/bin/env python3
"""Generate a Chinese text PDF question bank for manual/QA testing."""
from reportlab.lib.pagesizes import A4
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.pdfgen import canvas

FONT_PATH = "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
pdfmetrics.registerFont(TTFont("NotoCJK", FONT_PATH))

OUT = "计算机网络题库.pdf"
WIDTH, HEIGHT = A4

pages = [
    [
        "1. TCP 协议工作在 OSI 模型的哪一层？",
        "A. 物理层",
        "B. 传输层",
        "C. 网络层",
        "D. 应用层",
        "答案：B",
        "解析：TCP 是传输层协议，提供面向连接的可靠传输。",
        "",
        "2. 下列属于传输层协议的是？",
        "A. IP",
        "B. TCP",
        "C. ICMP",
        "D. ARP",
        "答案：B",
        "",
        "3. 以下哪些是面向连接的协议？",
        "A. TCP",
        "B. UDP",
        "C. SCTP",
        "D. IP",
        "正确答案：A、C",
    ],
    [
        "4. 判断：TCP 是面向连接的协议。",
        "答案：对",
        "",
        "5. 判断：UDP 提供可靠传输。",
        "答案：错",
        "",
        "6. 下列哪个是私有地址？",
        "A. 8.8.8.8",
        "B. 192.168.1.1",
        "答案：B",
        "",
        "7. 以下哪个是操作系统？",
        "A. Windows B. Linux C. MySQL D. Oracle",
        "答案：A",
    ],
]


def draw_page(c: canvas.Canvas, lines: list[str], page_no: int, total: int) -> None:
    c.setFont("NotoCJK", 10)
    c.setFillColorRGB(0.4, 0.4, 0.4)
    c.drawString(60, HEIGHT - 40, "计算机网络题库（测试）")
    c.drawCentredString(WIDTH / 2, 30, f"第 {page_no} 页 / 共 {total} 页")

    c.setFillColorRGB(0, 0, 0)
    c.setFont("NotoCJK", 12)
    y = HEIGHT - 80
    for line in lines:
        if line:
            c.drawString(60, y, line)
        y -= 22
    c.showPage()


def main() -> None:
    c = canvas.Canvas(OUT, pagesize=A4)
    for index, lines in enumerate(pages, start=1):
        draw_page(c, lines, index, len(pages))
    c.save()
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
