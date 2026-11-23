#!/usr/bin/env python3
"""Confere tools/popular/popular.txt no banco de DATs e gera PopularGames.kt.

Cada nome (ou alternativa) precisa existir no banco depois de normalizado do mesmo jeito que
o app normaliza (PopularGames.normalize). Nomes sem correspondência são listados e o script
falha, para a lista nunca ter entradas mortas.
"""
import re
import sqlite3
import sys
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SRC = ROOT / "tools/popular/popular.txt"
DB = ROOT / "data/src/main/assets/db/retroplay-dat.sqlite"
OUT = ROOT / "domain/src/main/kotlin/com/retroplay/domain/library/PopularGamesData.kt"


def normalize(s: str) -> str:
    s = unicodedata.normalize("NFD", s)
    s = "".join(c for c in s if unicodedata.category(c) != "Mn").lower()
    s = re.sub(r"\([^)]*\)|\[[^\]]*\]", " ", s)
    s = s.replace("'", "")
    s = re.sub(r"[^a-z0-9]+", " ", s)
    return " ".join(t for t in s.split() if t != "the")


def parse():
    lists, current = {}, None
    for line in SRC.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        m = re.fullmatch(r"\[(\w+)\]", line)
        if m:
            current = m.group(1)
            lists[current] = []
            continue
        lists[current].append([a.strip() for a in line.split("|") if a.strip()])
    return lists


def main():
    lists = parse()
    db = sqlite3.connect(DB)
    problems = []
    for system, entries in lists.items():
        titles = {normalize(t) for (t,) in db.execute("select title from game where system=?", (system,)) if t}
        romsets = {r for (r,) in db.execute("select romset from game where system=? and romset is not null", (system,))}
        seen = set()
        for entry in entries:
            ok = any(normalize(a) in titles or a in romsets for a in entry)
            if not ok:
                problems.append(f"{system}: {' | '.join(entry)}")
            key = normalize(entry[0])
            if key in seen:
                problems.append(f"{system}: repetido {entry[0]}")
            seen.add(key)
    if problems:
        print("Sem correspondência no banco:\n  " + "\n  ".join(problems))
        sys.exit(1)

    def kt(s):
        return '"' + s.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$") + '"'

    lines = [
        "package com.retroplay.domain.library",
        "",
        "// GERADO por tools/popular/build_popular.py a partir de tools/popular/popular.txt. Não edite.",
        "",
        "/** Por console: lista em ordem de popularidade; cada item são os nomes aceitos (título ou romset). */",
        "internal val POPULAR_LISTS: Map<String, List<List<String>>> = mapOf(",
    ]
    for system, entries in lists.items():
        lines.append(f"    {kt(system)} to listOf(")
        for entry in entries:
            lines.append("        listOf(" + ", ".join(kt(a) for a in entry) + "),")
        lines.append("    ),")
    lines.append(")")
    OUT.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(OUT.relative_to(ROOT), {s: len(e) for s, e in lists.items()})


if __name__ == "__main__":
    main()
