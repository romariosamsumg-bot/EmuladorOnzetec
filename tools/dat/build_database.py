#!/usr/bin/env python3
"""Gera o banco de identificação de jogos do Retrô Play (SQLite compacto).

Fontes (baixadas para um cache local na primeira execução):
  * libretro-database (CC BY-SA 4.0): DATs No-Intro + metadados (gênero, ano, desenvolvedora,
    editora, jogadores) de SNES, Mega Drive e Master System;
  * FinalBurn Neo: DAT "Neogeo only" (nomes dos romsets e CRCs dos arquivos internos);
  * libretro-thumbnails: lista de capas existentes (só os nomes; nenhuma imagem é baixada).

Uso: python3 tools/dat/build_database.py [saida.sqlite] [cache_dir]
"""
import os
import re
import sqlite3
import subprocess
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from datetime import date

DB_VERSION = 1
LIBRETRO_DB = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
FBNEO_DAT = "https://raw.githubusercontent.com/libretro/FBNeo/master/dats/FinalBurn%20Neo%20(ClrMame%20Pro%20XML%2C%20Neogeo%20only).dat"

# id do sistema no app -> nome no libretro-database / libretro-thumbnails
SYSTEMS = {
    "snes": "Nintendo - Super Nintendo Entertainment System",
    "megadrive": "Sega - Mega Drive - Genesis",
    "mastersystem": "Sega - Master System - Mark III",
    "neogeo": "SNK - Neo Geo",
}
META_FIELDS = ["genre", "releaseyear", "developer", "publisher", "maxusers"]
THUMB_TYPES = {"Named_Boxarts": 1, "Named_Titles": 2, "Named_Snaps": 4}
REGION_PREFERENCE = ["(USA", "(World", "(Europe", "(Brazil", "(Japan"]


def fetch(url, path):
    if not os.path.exists(path):
        print("baixando", url)
        req = urllib.request.Request(url, headers={"User-Agent": "retroplay-dat-builder"})
        with urllib.request.urlopen(req, timeout=120) as r, open(path, "wb") as f:
            f.write(r.read())
    return path


# ------------------------------------------------------------------ clrmamepro
TOKEN = re.compile(r'"((?:[^"\\]|\\.)*)"|(\(|\))|([^\s()"]+)')


def parse_clrmamepro(text):
    """Converte o formato clrmamepro em listas de dicionários (blocos 'game')."""
    tokens = [(m.group(1), m.group(2), m.group(3)) for m in TOKEN.finditer(text)]
    pos = 0

    def value():
        nonlocal pos
        s, p, w = tokens[pos]
        pos += 1
        if p == "(":
            return block()
        return s if s is not None else w

    def block():
        nonlocal pos
        out = []
        while pos < len(tokens):
            s, p, w = tokens[pos]
            if p == ")":
                pos += 1
                return out
            pos += 1
            key = w if w is not None else s
            out.append((key, value()))
        return out

    games = []
    while pos < len(tokens):
        key = tokens[pos][2]
        pos += 1
        v = value()
        if key == "game":
            games.append(v)
    return games


def get(block, key):
    for k, v in block:
        if k == key:
            return v
    return None


def get_all(block, key):
    return [v for k, v in block if k == key]


# ------------------------------------------------------------------ títulos
def display_title(name):
    """'Legend of Zelda, The - A Link to the Past (USA)' -> 'The Legend of Zelda - A Link to the Past'."""
    base = re.sub(r"\s*[\(\[].*$", "", name).strip()
    parts = base.split(" - ", 1)
    head = parts[0]
    m = re.match(r"^(.*), (The|A|An|O|Os|A|As|El|La|Los|Las|Die|Der|Das|Le|Les)$", head)
    if m:
        head = f"{m.group(2)} {m.group(1)}"
    return head if len(parts) == 1 else f"{head} - {parts[1]}"


def region_of(name):
    m = re.search(r"\(([^)]*(?:USA|Europe|Japan|World|Brazil|Korea|Asia|Spain|France|Germany|Italy)[^)]*)\)", name)
    return m.group(1) if m else None


def thumb_sanitize(name):
    return re.sub(r'[&*/:`<>?\\|"]', "_", name)


def base_key(name):
    return re.sub(r"\s*[\(\[].*$", "", name).strip().lower()


# ------------------------------------------------------------------ capas
def list_thumbnails(system_name, cache):
    repo = system_name.replace(" ", "_")
    target = os.path.join(cache, "thumbs", repo)
    if not os.path.isdir(target):
        os.makedirs(os.path.dirname(target), exist_ok=True)
        subprocess.run(
            ["git", "clone", "-q", "--filter=blob:none", "--no-checkout", "--depth", "1",
             f"https://github.com/libretro-thumbnails/{repo}.git", target],
            check=True,
        )
    out = subprocess.run(["git", "-C", target, "ls-tree", "-r", "--name-only", "HEAD"],
                         check=True, capture_output=True, text=True).stdout
    found = {t: set() for t in THUMB_TYPES}
    for line in out.splitlines():
        folder, _, file = line.partition("/")
        if folder in found and file.endswith(".png"):
            found[folder].add(file[:-4])
    return found


def thumb_info(name, thumbs, by_base):
    """Retorna (bitmask, nome_alternativo|None)."""
    s = thumb_sanitize(name)
    mask = sum(bit for t, bit in THUMB_TYPES.items() if s in thumbs[t])
    if mask:
        return mask, None
    # Sem capa com o nome exato: usa outra versão do mesmo jogo (outra região/revisão).
    candidates = by_base.get(base_key(s), [])
    if not candidates:
        return 0, None

    def rank(c):
        for i, r in enumerate(REGION_PREFERENCE):
            if r in c:
                return i
        return len(REGION_PREFERENCE)

    best = sorted(candidates, key=lambda c: (rank(c), len(c)))[0]
    mask = sum(bit for t, bit in THUMB_TYPES.items() if best in thumbs[t])
    return mask, best


def index_by_base(thumbs):
    idx = {}
    for t in THUMB_TYPES:
        for n in thumbs[t]:
            idx.setdefault(base_key(n), set()).add(n)
    return {k: list(v) for k, v in idx.items()}


# ------------------------------------------------------------------ geração
def build(out_path, cache):
    os.makedirs(cache, exist_ok=True)
    if os.path.exists(out_path):
        os.remove(out_path)
    db = sqlite3.connect(out_path)
    db.executescript("""
        PRAGMA page_size = 4096;
        CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);
        CREATE TABLE game(
            id INTEGER PRIMARY KEY,
            system TEXT NOT NULL,
            name TEXT NOT NULL,          -- nome canônico do DAT
            title TEXT NOT NULL,         -- nome amigável
            region TEXT,
            year TEXT,
            developer TEXT,
            publisher TEXT,
            genre TEXT,
            players INTEGER,
            thumbs INTEGER NOT NULL DEFAULT 0,  -- 1 boxart, 2 title, 4 snap
            thumb_name TEXT,             -- nome da capa quando difere de 'name'
            romset TEXT,                 -- arcade: nome do .zip (ex.: kof98)
            parent TEXT,                 -- arcade: romset pai (clone of)
            is_bios INTEGER NOT NULL DEFAULT 0
        );
        CREATE TABLE rom(
            game_id INTEGER NOT NULL,
            name TEXT NOT NULL,
            size INTEGER NOT NULL,
            crc INTEGER NOT NULL,
            md5 BLOB,
            merge INTEGER NOT NULL DEFAULT 0   -- arcade: arquivo pode vir do pai/BIOS
        );
    """)
    counts = {}
    for sid, sname in SYSTEMS.items():
        thumbs = list_thumbnails(sname, cache)
        by_base = index_by_base(thumbs)
        q = urllib.parse.quote(sname)
        if sid == "neogeo":
            counts[sid] = build_neogeo(db, cache, thumbs, by_base)
            continue
        meta = {}
        for field in META_FIELDS:
            path = fetch(f"{LIBRETRO_DB}/{field}/{q}.dat", os.path.join(cache, f"{field}-{sid}.dat"))
            for g in parse_clrmamepro(open(path, encoding="utf-8", errors="replace").read()):
                rom = get(g, "rom")
                crc = rom and get(rom, "crc")
                val = get(g, "users" if field == "maxusers" else field)
                if crc and val:
                    meta.setdefault(int(crc, 16), {})[field] = val
        path = fetch(f"{LIBRETRO_DB}/no-intro/{q}.dat", os.path.join(cache, f"no-intro-{sid}.dat"))
        n = 0
        for g in parse_clrmamepro(open(path, encoding="utf-8", errors="replace").read()):
            name = get(g, "name")
            roms = get_all(g, "rom")
            if not name or not roms:
                continue
            first_crc = next((int(get(r, "crc"), 16) for r in roms if get(r, "crc")), None)
            m = meta.get(first_crc, {})
            mask, alt = thumb_info(name, thumbs, by_base)
            players = m.get("maxusers")
            cur = db.execute(
                "INSERT INTO game(system,name,title,region,year,developer,publisher,genre,players,thumbs,thumb_name)"
                " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                (sid, name, display_title(name), region_of(name), m.get("releaseyear"), m.get("developer"),
                 m.get("publisher"), m.get("genre"), int(players) if players and players.isdigit() else None,
                 mask, alt),
            )
            for r in roms:
                crc = get(r, "crc")
                if not crc:
                    continue
                md5 = get(r, "md5")
                db.execute("INSERT INTO rom(game_id,name,size,crc,md5) VALUES(?,?,?,?,?)",
                           (cur.lastrowid, get(r, "name") or "", int(get(r, "size") or 0), int(crc, 16),
                            bytes.fromhex(md5) if md5 else None))
            n += 1
        counts[sid] = n
    db.execute("CREATE INDEX rom_crc ON rom(crc)")
    db.execute("CREATE INDEX rom_game ON rom(game_id)")
    db.execute("CREATE INDEX game_romset ON game(system, romset)")
    db.execute("CREATE INDEX game_title ON game(system, title)")
    for k, v in {"version": str(DB_VERSION), "generated": date.today().isoformat(),
                 "sources": "libretro-database (CC BY-SA 4.0); FinalBurn Neo DAT; libretro-thumbnails",
                 **{f"count_{k}": str(v) for k, v in counts.items()}}.items():
        db.execute("INSERT INTO meta VALUES(?,?)", (k, v))
    db.commit()
    db.execute("VACUUM")
    db.close()
    print("jogos por sistema:", counts, "| tamanho:", os.path.getsize(out_path), "bytes")


def build_neogeo(db, cache, thumbs, by_base):
    path = fetch(FBNEO_DAT, os.path.join(cache, "fbneo-neogeo.dat"))
    root = ET.parse(path).getroot()
    version = root.findtext("header/version") or "?"
    db.execute("INSERT OR REPLACE INTO meta VALUES('fbneo_dat_version', ?)", (version,))
    n = 0
    for g in root.iter("game"):
        setname = g.get("name")
        desc = g.findtext("description") or setname
        is_bios = 1 if g.get("isbios") == "yes" else 0
        mask, alt = thumb_info(desc, thumbs, by_base)
        title = display_title(desc.split(" / ")[0])
        cur = db.execute(
            "INSERT INTO game(system,name,title,year,developer,genre,thumbs,thumb_name,romset,parent,is_bios)"
            " VALUES('neogeo',?,?,?,?,?,?,?,?,?,?)",
            (desc, title, g.findtext("year"), g.findtext("manufacturer"), None, mask, alt,
             setname, g.get("cloneof"), is_bios),
        )
        for r in g.iter("rom"):
            if r.get("status") == "nodump" or not r.get("crc"):
                continue
            db.execute("INSERT INTO rom(game_id,name,size,crc,merge) VALUES(?,?,?,?,?)",
                       (cur.lastrowid, r.get("name"), int(r.get("size") or 0), int(r.get("crc"), 16),
                        1 if r.get("merge") else 0))
        n += 1
    return n


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "retroplay-dat.sqlite"
    cache = sys.argv[2] if len(sys.argv) > 2 else os.path.join(os.path.dirname(os.path.abspath(__file__)), ".cache")
    build(out, cache)
