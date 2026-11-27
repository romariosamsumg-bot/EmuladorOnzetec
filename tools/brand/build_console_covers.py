#!/usr/bin/env python3
"""Corta as grades 2x2 e as imagens avulsas nas capas dos consoles.

- consoles_grid.png: SNES, Mega Drive / Master System, Arcade.
- consoles_grid2.png: Nintendinho, Game Boy / Game Boy Color, Game Boy Advance.
- console_psx.png, console_atari2600.png: uma imagem por console, cortada no centro.
As faixas pretas entre os quadros são achadas sozinhas.
Saída: app/src/main/res/drawable-nodpi/cover_<console>.webp (3:2, 660x440).
"""
import sys
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
BRAND = ROOT / "tools/brand"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
GRIDS = {
    "consoles_grid.png": [["snes", "megadrive"], ["mastersystem", "arcade"]],
    "consoles_grid2.png": [["nes", "gb"], ["gbc", "gba"]],
}
SINGLES = {"console_psx.png": "psx", "console_atari2600.png": "atari2600"}
SIZE = (660, 440)


def gutter(profile, lo, hi):
    """Faixa escura mais longa entre lo e hi (índices). Retorna (início, fim)."""
    dark = [v < 18 for v in profile]
    best, start = (0, 0), None
    for i in range(lo, hi):
        if dark[i] and start is None:
            start = i
        if (not dark[i] or i == hi - 1) and start is not None:
            end = i if not dark[i] else i + 1
            if end - start > best[1] - best[0]:
                best = (start, end)
            start = None
    if best == (0, 0):
        sys.exit("faixa preta entre os quadros não encontrada")
    return best


def save(cell, name, box):
    """Corta no centro para 3:2 e grava cover_<name>.webp."""
    ratio = SIZE[0] / SIZE[1]
    cw, ch = cell.size
    if cw / ch > ratio:
        nw = round(ch * ratio); cell = cell.crop(((cw - nw) // 2, 0, (cw - nw) // 2 + nw, ch))
    else:
        nh = round(cw / ratio); cell = cell.crop((0, (ch - nh) // 2, cw, (ch - nh) // 2 + nh))
    cell = cell.resize(SIZE, Image.LANCZOS)
    dst = OUT / f"cover_{name}.webp"
    cell.save(dst, "WEBP", quality=88, method=6)
    print(dst.relative_to(ROOT), box, dst.stat().st_size // 1024, "KB")


def cut_grid(src, names):
    im = Image.open(src).convert("RGB")
    w, h = im.size
    g = im.convert("L")
    px = g.load()
    cols = [sum(px[x, y] for y in range(0, h, 4)) / len(range(0, h, 4)) for x in range(w)]
    rows = [sum(px[x, y] for x in range(0, w, 4)) / len(range(0, w, 4)) for y in range(h)]
    gx = gutter(cols, int(w * 0.4), int(w * 0.6))
    gy = gutter(rows, int(h * 0.4), int(h * 0.6))
    # Borda externa escura (se houver) também é ignorada.
    left = next(x for x in range(w) if cols[x] >= 18)
    right = next(x for x in range(w - 1, -1, -1) if cols[x] >= 18) + 1
    top = next(y for y in range(h) if rows[y] >= 18)
    bottom = next(y for y in range(h - 1, -1, -1) if rows[y] >= 18) + 1
    inset = 3
    xs = [(left + inset, gx[0] - inset), (gx[1] + inset, right - inset)]
    ys = [(top + inset, gy[0] - inset), (gy[1] + inset, bottom - inset)]
    for r, (y0, y1) in enumerate(ys):
        for c, (x0, x1) in enumerate(xs):
            save(im.crop((x0, y0, x1, y1)), names[r][c], (x0, y0, x1, y1))


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    only = set(sys.argv[1:])  # opcional: só estes arquivos de origem
    for src, names in GRIDS.items():
        if not only or src in only:
            cut_grid(BRAND / src, names)
    for src, name in SINGLES.items():
        if not only or src in only:
            im = Image.open(BRAND / src).convert("RGB")
            save(im, name, (0, 0) + im.size)


if __name__ == "__main__":
    main()
