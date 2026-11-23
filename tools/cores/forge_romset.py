#!/usr/bin/env python3
"""Gera um romset SINTÉTICO (sem conteúdo de jogo) com os nomes, tamanhos e CRCs de um DAT.

Uso: python3 tools/cores/forge_romset.py <dat.xml> <romset> <pasta_saida>
Os arquivos são bytes sem sentido com o CRC32 ajustado: o núcleo aceita o romset (confere
nome/tamanho/CRC) e carrega o jogo, o que basta para testar caminhos de pai/BIOS.
"""
import os
import sys
import xml.etree.ElementTree as ET
import zipfile
import zlib


def _table():
    t = []
    for i in range(256):
        c = i
        for _ in range(8):
            c = (c >> 1) ^ 0xEDB88320 if c & 1 else c >> 1
        t.append(c)
    return t


T = _table()
INV = {v >> 24: i for i, v in enumerate(T)}


def forge(size, target):
    """Bytes de tamanho `size` cujo CRC32 é `target` (ajusta os 4 últimos bytes)."""
    body = bytearray((i * 37 + 11) & 0xFF for i in range(max(size - 4, 0)))
    if size < 4:
        return bytes(size)
    crc = zlib.crc32(body) ^ 0xFFFFFFFF
    want = target ^ 0xFFFFFFFF
    # Trabalha de trás para frente pelo registrador do CRC.
    idx = []
    reg = want
    for _ in range(4):
        i = INV[reg >> 24]
        idx.append(i)
        reg = ((reg ^ T[i]) << 8) & 0xFFFFFFFF
    patch = bytearray()
    cur = crc
    for i in reversed(idx):
        b = (cur ^ i) & 0xFF
        patch.append(b)
        cur = (cur >> 8) ^ T[(cur ^ b) & 0xFF]
    out = bytes(body + patch)
    assert zlib.crc32(out) == target, "falha ao ajustar o CRC"
    return out


def build(dat, setname, out_dir):
    root = ET.parse(dat).getroot()
    g = next(x for x in root.iter("game") if x.get("name") == setname)
    os.makedirs(out_dir, exist_ok=True)
    path = os.path.join(out_dir, setname + ".zip")
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as z:
        for r in g.iter("rom"):
            if r.get("merge") or not r.get("crc"):
                continue
            z.writestr(r.get("name"), forge(int(r.get("size")), int(r.get("crc"), 16)))
    return path


if __name__ == "__main__":
    print(build(sys.argv[1], sys.argv[2], sys.argv[3]))
