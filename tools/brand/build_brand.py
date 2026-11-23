#!/usr/bin/env python3
"""Gera ícone, splash, banner de TV e logo do app a partir das imagens da marca.

Fontes: tools/brand/icone.png (TV quadrada, fundo azul-marinho) e tools/brand/logo.png (logo com
fundo transparente). Uso: python3 tools/brand/build_brand.py  (requer Pillow)
"""
import os

from PIL import Image, ImageDraw, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "app", "src", "main", "res")
NAVY = (1, 28, 56)
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}


def out(folder, name, img):
    d = os.path.join(RES, folder)
    os.makedirs(d, exist_ok=True)
    img.save(os.path.join(d, name), optimize=True)


def centered(canvas_px, img, content_px):
    canvas = Image.new("RGBA", (canvas_px, canvas_px), (0, 0, 0, 0))
    scaled = img.resize((content_px, content_px), Image.LANCZOS)
    off = (canvas_px - content_px) // 2
    canvas.paste(scaled, (off, off), scaled)
    return canvas


def rounded(img, radius_frac=0.14):
    """Recorta a TV em quadrado arredondado (some o fundo nos cantos do arquivo)."""
    mask = Image.new("L", img.size, 0)
    r = int(img.width * radius_frac)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, img.width - 1, img.height - 1), r, fill=255)
    out_img = img.convert("RGBA")
    out_img.putalpha(mask)
    return out_img


def monochrome(icon):
    """Silhueta branca dos símbolos acesos da tela (direcional, botões e nome)."""
    rgb = icon.convert("RGB")
    w, h = rgb.size
    mask = Image.new("L", (w, h), 0)
    px, mp = rgb.load(), mask.load()
    for y in range(int(h * 0.2), int(h * 0.75)):
        for x in range(int(w * 0.12), int(w * 0.88)):
            r, g, b = px[x, y]
            if r > 150 and r > b + 60:  # creme e laranja; o fundo da tela é azul
                mp[x, y] = 255
    mask = mask.filter(ImageFilter.MaxFilter(5)).filter(ImageFilter.MinFilter(3))
    sil = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    sil.putalpha(mask)
    return sil.crop(mask.getbbox())


def main():
    icon = Image.open(os.path.join(HERE, "icone.png")).convert("RGB")
    logo = Image.open(os.path.join(HERE, "logo.png")).convert("RGBA")
    logo = logo.crop(logo.getchannel("A").point(lambda v: 255 if v > 20 else 0).getbbox())

    # Ícone adaptativo: tela de 108 dp; a TV ocupa ~80 dp (dentro da área visível das máscaras).
    tv = rounded(icon)
    sil = monochrome(icon)
    for folder, k in DENSITIES.items():
        size = int(108 * k)
        out(f"drawable-{folder}", "ic_launcher_foreground.png", centered(size, tv, int(80 * k)))
        # Monocromático (ícones temáticos do Android 13+): só os símbolos, na zona segura de 66 dp.
        m = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        sw = int(60 * k)
        sh = int(sil.height * sw / sil.width)
        s = sil.resize((sw, sh), Image.LANCZOS)
        m.paste(s, ((size - sw) // 2, (size - sh) // 2), s)
        out(f"drawable-{folder}", "ic_launcher_monochrome.png", m)

    # Splash (Android 12+): ícone sem fundo numa área de 288 dp; tem de caber no círculo de 192 dp.
    out("drawable-xxxhdpi", "splash_icon.png", centered(288 * 4, tv, 140 * 4))

    # Banner da TV: 320×180 dp (640×360 px em xhdpi).
    w, h = 640, 360
    banner = Image.new("RGB", (w, h), NAVY)
    grad = Image.new("RGB", (w, h))
    gd = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / (h - 1)
        gd.line([(0, y), (w, y)], fill=tuple(int(NAVY[i] * (1 - t) + c * t) for i, c in enumerate((0, 12, 28))))
    banner.paste(grad)
    lw = int(w * 0.86)
    lh = int(logo.height * lw / logo.width)
    if lh > h * 0.86:
        lh = int(h * 0.86)
        lw = int(logo.width * lh / logo.height)
    l = logo.resize((lw, lh), Image.LANCZOS)
    banner.paste(l, ((w - lw) // 2, (h - lh) // 2), l)
    out("drawable-xhdpi", "banner.png", banner)

    # Logo para dentro do app (tela inicial vazia, Sobre).
    lw = 900
    out("drawable-nodpi", "logo_onzetec.png", logo.resize((lw, int(logo.height * lw / logo.width)), Image.LANCZOS))
    print("ok")


if __name__ == "__main__":
    main()
