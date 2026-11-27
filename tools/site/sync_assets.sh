#!/usr/bin/env bash
# Copia a marca, as capas dos consoles e os sons do app para o site (site/assets).
# Rode depois de mudar o logo, as capas (tools/brand) ou os sons (tools/sound).
set -euo pipefail
cd "$(dirname "$0")/../.."
D=site/assets
cp app/src/main/res/drawable-nodpi/cover_*.webp "$D/img/consoles/"
for f in "$D"/img/consoles/cover_*.webp; do mv "$f" "$D/img/consoles/$(basename "$f" | sed 's/^cover_//')"; done
python3 - <<'PY'
from PIL import Image
logo = Image.open("app/src/main/res/drawable-nodpi/logo_onzetec.png").convert("RGBA")
logo.resize((640, 640 * logo.height // logo.width), Image.LANCZOS).save("site/assets/img/logo.webp", "WEBP", quality=88)
mark = Image.open("app/src/main/res/drawable-nodpi/logo_mark.png").convert("RGBA")
mark.resize((256, 256 * mark.height // mark.width), Image.LANCZOS).save("site/assets/img/mark.webp", "WEBP", quality=90)
icon = Image.open("tools/brand/icone.png").convert("RGBA")
for s in (192, 512):
    icon.resize((s, s), Image.LANCZOS).save(f"site/assets/img/icon-{s}.png")
icon.resize((48, 48), Image.LANCZOS).save("site/favicon.png")
PY
cp app/src/main/res/raw/music_menu.ogg "$D/audio/music.ogg"
for s in select move back success launch bump; do cp "app/src/main/res/raw/sfx_$s.ogg" "$D/audio/$s.ogg"; done
echo "ok"
