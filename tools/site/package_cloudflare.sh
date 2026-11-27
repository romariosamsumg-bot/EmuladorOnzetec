#!/usr/bin/env bash
# Empacota o site para o Cloudflare Pages (upload direto ou Git).
# Saída: build/site-cloudflare/ e build/OnzetecGames-site-cloudflare.zip
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
REL=https://github.com/romariosamsumg-bot/EmuladorOnzetec/releases/latest/download
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

/*.html
  Cache-Control: public, max-age=0, must-revalidate
H
(cd "$OUT" && zip -qr ../OnzetecGames-site-cloudflare.zip .)
echo "Pasta: $OUT"
echo "Zip:   build/OnzetecGames-site-cloudflare.zip ($(du -h build/OnzetecGames-site-cloudflare.zip | cut -f1))"
