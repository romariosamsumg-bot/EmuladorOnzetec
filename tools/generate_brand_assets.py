#!/usr/bin/env python3
"""Gera os recursos de marca do app (logo vetorial e banner de TV 320x180).

Uso: python3 tools/generate_brand_assets.py "Retrô Play"
O banner é bitmap (exigência do launcher de TV); rode de novo se trocar o nome do app.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")

# "Play" pixelado: colunas de um triângulo em grade 9x9.
HALF = [4, 4, 3, 3, 2, 2, 1, 1, 0]


def pixel_columns(x0, y0, px):
    for c, h in enumerate(HALF):
        top = 4 - h
        bottom = 4 + h + 1
        yield (x0 + c * px, y0 + top * px, x0 + (c + 1) * px, y0 + bottom * px)


def logo_path(x0, y0, px):
    parts = []
    for (l, t, r, b) in pixel_columns(x0, y0, px):
        parts.append(f"M{l:g},{t:g}H{r:g}V{b:g}H{l:g}Z")
    return "".join(parts)


def write_vector_foreground():
    # Ícone adaptativo: viewport 108, zona segura de 66 no centro.
    px = 5
    x0 = 54 - 9 * px / 2 + 2  # leve deslocamento óptico para a direita
    y0 = 54 - 9 * px / 2
    path = logo_path(x0, y0, px)
    xml = f"""<?xml version="1.0" encoding="utf-8"?>
<!-- Gerado por tools/generate_brand_assets.py -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp" android:height="108dp"
    android:viewportWidth="108" android:viewportHeight="108">
    <path android:fillColor="#FFFFFFFF" android:pathData="{path}"/>
</vector>
"""
    with open(os.path.join(RES, "drawable", "ic_launcher_foreground.xml"), "w") as f:
        f.write(xml)


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(h - 1, 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def write_banner(name):
    w, h = 320, 180
    img = gradient(w, h, (20, 14, 48), (8, 8, 14))
    d = ImageDraw.Draw(img)
    # Selo do logo
    d.rounded_rectangle((22, 50, 102, 130), radius=18, fill=(139, 108, 255))
    for box in pixel_columns(40, 68, 5):
        d.rectangle((box[0], box[1], box[2] - 1, box[3] - 1), fill=(255, 255, 255))
    font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
    size = 34
    font = ImageFont.truetype(font_path, size)
    while d.textlength(name, font=font) > w - 128 and size > 14:
        size -= 1
        font = ImageFont.truetype(font_path, size)
    bbox = d.textbbox((0, 0), name, font=font)
    th = bbox[3] - bbox[1]
    d.text((116, (h - th) // 2 - bbox[1]), name, font=font, fill=(242, 242, 247))
    out = os.path.join(RES, "drawable-xhdpi", "banner.png")
    img.save(out, optimize=True)


if __name__ == "__main__":
    app_name = sys.argv[1] if len(sys.argv) > 1 else "Retrô Play"
    write_vector_foreground()
    write_banner(app_name)
    print("ok")
