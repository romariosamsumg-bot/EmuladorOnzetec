#!/usr/bin/env bash
# Empacota o site para o Cloudflare Pages (upload direto ou Git).
# Saída: build/site-cloudflare/ e build/OnzetecGames-site-cloudflare.zip
# Com APK_DIR=pasta (contendo OnzetecGames.apk e OnzetecGames-Celular.apk), os APKs vão
# dentro do site (/downloads/) e os botões deixam de depender das Releases do GitHub.
# Diferenças para o GitHub Pages: o site fica na raiz do domínio (a página 404 usa "/"),
# /tv e /celular viram redirecionamentos de verdade (_redirects) e há cabeçalhos de cache
# e segurança (_headers).
set -euo pipefail
cd "$(dirname "$0")/../.."
OUT=build/site-cloudflare
rm -rf "$OUT" build/OnzetecGames-site-cloudflare.zip
mkdir -p "$OUT"
cp -r site/. "$OUT/"
rm -f "$OUT/.nojekyll"
# Raiz do domínio em vez de /EmuladorOnzetec/.
sed -i 's|/EmuladorOnzetec/|/|g' "$OUT/404.html"
# Os redirecionamentos substituem as páginas tv/ e celular/.
rm -rf "$OUT/tv" "$OUT/celular"
GH=https://github.com/romariosamsumg-bot/EmuladorOnzetec/releases/latest/download
REL=$GH
if [[ -n "${APK_DIR:-}" ]]; then
  mkdir -p "$OUT/downloads"
  cp "$APK_DIR/OnzetecGames.apk" "$APK_DIR/OnzetecGames-Celular.apk" "$OUT/downloads/"
  REL=/downloads
  # Botões e textos do site apontam para os APKs hospedados junto.
  grep -rl "$GH" "$OUT" --include=*.html | xargs -r sed -i "s|$GH|/downloads|g"
fi

# version.json: o app lê este arquivo para avisar de versão nova (Início, Sobre e notificação).
# A versão vem do próprio APK; as novidades, de tools/site/novidades.txt.
if [[ -n "${APK_DIR:-}" ]]; then
  SDK=$(grep -s sdk.dir local.properties | cut -d= -f2); SDK=${SDK:-${ANDROID_HOME:-}}
  AAPT=$(ls -d "$SDK"/build-tools/*/aapt2 2>/dev/null | tail -1)
  BADGE=$("$AAPT" dump badging "$APK_DIR/OnzetecGames.apk" 2>/dev/null | sed -n 1p)
  VCODE=$(sed -E "s/.*versionCode='([0-9]+)'.*/\1/" <<<"$BADGE")
  VNAME=$(sed -E "s/.*versionName='([^']+)'.*/\1/" <<<"$BADGE")
  python3 - "$OUT/version.json" "$VCODE" "$VNAME" tools/site/novidades.txt <<'PY'
import json, sys
out, code, name, notes_file = sys.argv[1:5]
notes = [l.strip() for l in open(notes_file, encoding="utf-8") if l.strip() and not l.startswith("#")]
json.dump({
    "versionCode": int(code),
    "versionName": name,
    "notes": notes,
    "apk": {"tv": "/downloads/OnzetecGames.apk", "mobile": "/downloads/OnzetecGames-Celular.apk"},
}, open(out, "w", encoding="utf-8"), ensure_ascii=False, indent=2)
PY
  echo "version.json: $VNAME ($VCODE)"
fi
cat > "$OUT/_redirects" <<R
/tv        $REL/OnzetecGames.apk          302
/tv/       $REL/OnzetecGames.apk          302
/celular   $REL/OnzetecGames-Celular.apk  302
/celular/  $REL/OnzetecGames-Celular.apk  302
/baixar    /#baixar                       302
R
cat > "$OUT/_headers" <<'H'
/*
  X-Content-Type-Options: nosniff
  Referrer-Policy: strict-origin-when-cross-origin
  Permissions-Policy: camera=(), microphone=(), geolocation=()
  X-Frame-Options: SAMEORIGIN

/assets/*
  Cache-Control: public, max-age=604800

/data/*
  Cache-Control: public, max-age=300

/version.json
  Cache-Control: no-cache
  Access-Control-Allow-Origin: *

/*.html
  Cache-Control: public, max-age=0, must-revalidate

/downloads/*
  Content-Type: application/vnd.android.package-archive
  Content-Disposition: attachment
  Cache-Control: public, max-age=3600
H
(cd "$OUT" && zip -qr ../OnzetecGames-site-cloudflare.zip .)
echo "Pasta: $OUT"
echo "Zip:   build/OnzetecGames-site-cloudflare.zip ($(du -h build/OnzetecGames-site-cloudflare.zip | cut -f1))"
