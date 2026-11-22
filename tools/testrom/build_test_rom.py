#!/usr/bin/env python3
"""Gera a ROM de teste do Retrô Play (SNES, LoROM, 128 KB). Código original, licença GPL-3.0.

O que a ROM faz (serve para testar vídeo, entrada e áudio do emulador):
  * D-pad move um quadrado branco de 32x32.
  * Cada botão muda a cor de fundo (B verde, A vermelho, Y amarelo, X azul, L magenta,
    R ciano, Start branco, Select cinza).
  * B / A / Y / X tocam tons diferentes no chip de som (SPC700/DSP).

Uso: python3 tools/testrom/build_test_rom.py <saida.sfc>
"""
import sys


class Asm:
    """Montador mínimo de bytes com rótulos e desvios relativos (65816 e SPC700)."""

    def __init__(self, origin):
        self.origin = origin
        self.out = bytearray()
        self.labels = {}
        self.fixups = []  # (pos, label, kind)

    @property
    def pc(self):
        return self.origin + len(self.out)

    def label(self, name):
        self.labels[name] = self.pc

    def db(self, *bs):
        for b in bs:
            self.out.append(b & 0xFF)

    def dw(self, w):
        self.db(w & 0xFF, w >> 8)

    def rel(self, opcode, label):
        self.db(opcode, 0)
        self.fixups.append((len(self.out) - 1, label, "rel"))

    def abs_label(self, opcode, label):
        self.db(opcode, 0, 0)
        self.fixups.append((len(self.out) - 2, label, "abs"))

    def resolve(self):
        for pos, label, kind in self.fixups:
            target = self.labels[label]
            if kind == "rel":
                off = target - (self.origin + pos + 1)
                assert -128 <= off <= 127, f"desvio longe demais para {label}"
                self.out[pos] = off & 0xFF
            else:
                self.out[pos] = target & 0xFF
                self.out[pos + 1] = (target >> 8) & 0xFF
        return bytes(self.out)


# ---------------------------------------------------------------- SPC700 (som)
def spc_program():
    a = Asm(0x0200)

    def dsp(reg, val):
        a.db(0x8F, reg, 0xF2)  # MOV $F2,#reg
        a.db(0x8F, val, 0xF3)  # MOV $F3,#val

    dsp(0x6C, 0x20)  # FLG: sem escrita de eco, sem mudo
    dsp(0x0C, 0x7F)  # volume principal E
    dsp(0x1C, 0x7F)  # volume principal D
    dsp(0x2C, 0x00)
    dsp(0x3C, 0x00)
    dsp(0x5D, 0x03)  # DIR = $0300
    dsp(0x00, 0x50)  # voz 0 volume E
    dsp(0x01, 0x50)  # voz 0 volume D
    dsp(0x04, 0x00)  # SRCN 0
    dsp(0x05, 0x8F)  # ADSR1: ataque rápido
    dsp(0x06, 0xE0)  # ADSR2: sustain máximo
    dsp(0x02, 0x00)  # pitch baixo
    dsp(0x3D, 0x00)
    dsp(0x2D, 0x00)
    dsp(0x4D, 0x00)
    a.db(0xE4, 0xF4)  # MOV A,$F4
    a.db(0xC4, 0x00)  # MOV $00,A  (último valor)
    a.label("loop")
    a.db(0xE4, 0xF4)  # MOV A,$F4
    a.db(0x64, 0x00)  # CMP A,$00
    a.rel(0xF0, "loop")  # BEQ loop
    a.db(0xC4, 0x00)  # MOV $00,A
    a.db(0x68, 0x00)  # CMP A,#0
    a.rel(0xF0, "off")
    a.db(0x8F, 0x03, 0xF2)  # MOV $F2,#$03 (pitch alto)
    a.db(0xC4, 0xF3)  # MOV $F3,A
    dsp(0x5C, 0x00)  # KOF = 0
    dsp(0x4C, 0x01)  # KON = voz 0
    a.rel(0x2F, "loop")  # BRA loop
    a.label("off")
    dsp(0x5C, 0x01)  # KOF = voz 0
    a.rel(0x2F, "loop")
    code = bytearray(a.resolve())
    assert len(code) <= 0x100
    code += bytes(0x100 - len(code))
    # Diretório de amostras em $0300: início e loop em $0310
    code += bytes([0x10, 0x03, 0x10, 0x03]) + bytes(12)
    # Amostra BRR: onda quadrada de 32 amostras (16 positivas + 16 negativas), em loop
    code += bytes([0xB0] + [0x77] * 8)
    code += bytes([0xB3] + [0x99] * 8)
    return bytes(code)


# ---------------------------------------------------------------- 65816 (CPU)
def cpu_program(spc):
    a = Asm(0x8000)
    X, Y, JOYL, JOYH, TONE = 0x00, 0x01, 0x02, 0x03, 0x04

    a.label("reset")
    a.db(0x78, 0x18, 0xFB)  # SEI; CLC; XCE (modo nativo)
    a.db(0xC2, 0x10)  # REP #$10 (X/Y 16 bits)
    a.db(0xE2, 0x20)  # SEP #$20 (A 8 bits)
    a.db(0xA2), a.dw(0x1FFF), a.db(0x9A)  # LDX #$1FFF; TXS
    a.db(0xA9, 0x8F, 0x8D), a.dw(0x2100)  # tela desligada
    a.db(0x9C), a.dw(0x4200)  # sem NMI por enquanto

    # --- envia o programa de som ao SPC700 (protocolo do IPL)
    a.label("w_aa")
    a.db(0xAD), a.dw(0x2140), a.db(0xC9, 0xAA)
    a.rel(0xD0, "w_aa")
    a.label("w_bb")
    a.db(0xAD), a.dw(0x2141), a.db(0xC9, 0xBB)
    a.rel(0xD0, "w_bb")
    a.db(0xA9, 0x00, 0x8D), a.dw(0x2142)
    a.db(0xA9, 0x02, 0x8D), a.dw(0x2143)  # destino $0200
    a.db(0xA9, 0x01, 0x8D), a.dw(0x2141)
    a.db(0xA9, 0xCC, 0x8D), a.dw(0x2140)
    a.label("w_cc")
    a.db(0xCD), a.dw(0x2140)
    a.rel(0xD0, "w_cc")
    a.db(0xA2), a.dw(0x0000)
    a.label("up_loop")
    a.abs_label(0xBD, "spc_data")  # LDA spc_data,X
    a.db(0x8D), a.dw(0x2141)
    a.db(0x8A)  # TXA (byte baixo)
    a.db(0x8D), a.dw(0x2140)
    a.label("up_wait")
    a.db(0xCD), a.dw(0x2140)
    a.rel(0xD0, "up_wait")
    a.db(0xE8)  # INX
    a.db(0xE0), a.dw(len(spc))  # CPX #len
    a.rel(0xD0, "up_loop")
    a.db(0xA9, 0x00, 0x8D), a.dw(0x2142)
    a.db(0xA9, 0x02, 0x8D), a.dw(0x2143)  # entrada $0200
    a.db(0x9C), a.dw(0x2141)  # STZ $2141 → executar
    a.db(0x8A, 0x1A, 0x1A)  # TXA; INC A; INC A
    a.rel(0xD0, "nz")
    a.db(0x1A)
    a.label("nz")
    a.db(0x8D), a.dw(0x2140)
    a.label("up_done")
    a.db(0xCD), a.dw(0x2140)
    a.rel(0xD0, "up_done")

    # --- vídeo: sprites 16x16/32x32, todos os tiles sólidos com a cor 1
    a.db(0xA9, 0x60, 0x8D), a.dw(0x2101)  # OBSEL
    a.db(0x9C), a.dw(0x2105)  # BGMODE 0
    a.db(0xA9, 0x80, 0x8D), a.dw(0x2115)  # VMAIN
    a.db(0x9C), a.dw(0x2116)
    a.db(0x9C), a.dw(0x2117)
    a.db(0xA2), a.dw(0x0000)
    a.label("vram")
    a.db(0x8A, 0x29, 0x08)  # TXA; AND #8
    a.rel(0xD0, "vzero")
    a.db(0xA9, 0xFF, 0x8D), a.dw(0x2118)
    a.db(0x9C), a.dw(0x2119)
    a.rel(0x80, "vnext")
    a.label("vzero")
    a.db(0x9C), a.dw(0x2118)
    a.db(0x9C), a.dw(0x2119)
    a.label("vnext")
    a.db(0xE8, 0xE0), a.dw(1024)
    a.rel(0xD0, "vram")
    # paleta: cor 129 (sprite paleta 0, índice 1) = branco
    a.db(0xA9, 0x81, 0x8D), a.dw(0x2121)
    a.db(0xA9, 0xFF, 0x8D), a.dw(0x2122)
    a.db(0xA9, 0x7F, 0x8D), a.dw(0x2122)
    # OAM: todos os sprites fora da tela; sprite 0 grande (32x32)
    a.db(0x9C), a.dw(0x2102)
    a.db(0x9C), a.dw(0x2103)
    a.db(0xA2), a.dw(0x0000)
    a.label("oam")
    a.db(0x9C), a.dw(0x2104)  # X
    a.db(0xA9, 0xF0, 0x8D), a.dw(0x2104)  # Y
    a.db(0x9C), a.dw(0x2104)  # tile 0
    a.db(0xA9, 0x30, 0x8D), a.dw(0x2104)  # prioridade 3
    a.db(0xE8, 0xE0), a.dw(128)
    a.rel(0xD0, "oam")
    a.db(0xA9, 0x02, 0x8D), a.dw(0x2104)  # tabela alta: sprite 0 grande
    a.db(0xA2), a.dw(1)
    a.label("oamh")
    a.db(0x9C), a.dw(0x2104)
    a.db(0xE8, 0xE0), a.dw(32)
    a.rel(0xD0, "oamh")
    a.db(0xA9, 0x10, 0x8D), a.dw(0x212C)  # só sprites na tela principal
    a.db(0x9C), a.dw(0x2130)
    a.db(0x9C), a.dw(0x2131)
    a.db(0xA9, 112, 0x85, X)  # posição inicial
    a.db(0xA9, 96, 0x85, Y)
    a.db(0x64, TONE)  # STZ tone
    a.db(0xA9, 0x0F, 0x8D), a.dw(0x2100)  # tela ligada
    a.db(0xA9, 0x81, 0x8D), a.dw(0x4200)  # NMI + leitura automática do controle

    # --- laço principal (1x por quadro)
    a.label("main")
    a.db(0xCB)  # WAI
    a.label("joyw")
    a.db(0xAD), a.dw(0x4212), a.db(0x29, 0x01)
    a.rel(0xD0, "joyw")
    a.db(0xAD), a.dw(0x4218), a.db(0x85, JOYL)
    a.db(0xAD), a.dw(0x4219), a.db(0x85, JOYH)
    # movimento
    for bit, var, delta in ((0x08, Y, -2), (0x04, Y, 2), (0x02, X, -2), (0x01, X, 2)):
        skip = f"m{bit}"
        a.db(0xA5, JOYH, 0x29, bit)
        a.rel(0xF0, skip)
        a.db(0xA5, var, 0x18 if delta > 0 else 0x38)  # LDA var; CLC/SEC
        a.db(0x69 if delta > 0 else 0xE9, abs(delta))  # ADC/SBC
        a.db(0x85, var)
        a.label(skip)
    a.db(0x9C), a.dw(0x2102)
    a.db(0x9C), a.dw(0x2103)
    a.db(0xA5, X, 0x8D), a.dw(0x2104)
    a.db(0xA5, Y, 0x8D), a.dw(0x2104)
    # cor de fundo e tom: primeira regra que casar vence
    rules = [  # (registro, bit, cor BGR555, tom)
        (JOYH, 0x80, 0x03E0, 0x04),  # B
        (JOYL, 0x80, 0x001F, 0x05),  # A
        (JOYH, 0x40, 0x03FF, 0x06),  # Y
        (JOYL, 0x40, 0x7C00, 0x08),  # X
        (JOYL, 0x20, 0x7C1F, 0x00),  # L
        (JOYL, 0x10, 0x7FE0, 0x00),  # R
        (JOYH, 0x10, 0x5294, 0x00),  # Start
        (JOYH, 0x20, 0x2108, 0x00),  # Select
    ]
    a.db(0x9C), a.dw(0x2121)  # cor 0 (fundo)
    for i, (reg, bit, color, tone) in enumerate(rules):
        a.db(0xA5, reg, 0x29, bit)
        a.rel(0xF0, f"r{i}")
        a.db(0xA9, color & 0xFF, 0x8D), a.dw(0x2122)
        a.db(0xA9, color >> 8, 0x8D), a.dw(0x2122)
        a.db(0xA9, tone, 0x8D), a.dw(0x2140)
        a.abs_label(0x4C, "main")  # JMP main
        a.label(f"r{i}")
    a.db(0xA9, 0x00, 0x8D), a.dw(0x2122)  # nenhum botão: azul-escuro
    a.db(0xA9, 0x28, 0x8D), a.dw(0x2122)
    a.db(0x9C), a.dw(0x2140)
    a.abs_label(0x4C, "main")

    a.label("rti")
    a.db(0x40)
    a.label("spc_data")
    a.db(*spc)
    return a.resolve(), a.labels["reset"], a.labels["rti"]


def build():
    spc = spc_program()
    code, reset, rti = cpu_program(spc)
    rom = bytearray(0x8000)
    rom[0:len(code)] = code
    title = b"RETRO PLAY TESTE".ljust(21, b" ")
    rom[0x7FC0:0x7FD5] = title
    rom[0x7FD5] = 0x20  # LoROM
    rom[0x7FD6] = 0x00  # só ROM
    rom[0x7FD7] = 0x07  # 128 KB (os núcleos rejeitam valores menores)
    rom[0x7FD8] = 0x00  # sem SRAM
    rom[0x7FD9] = 0x01  # região: América do Norte (NTSC)
    rom[0x7FDA] = 0x00
    rom[0x7FDB] = 0x00
    for vec in (0x7FE4, 0x7FE6, 0x7FE8, 0x7FEA, 0x7FEE, 0x7FF4, 0x7FF8, 0x7FFA, 0x7FFE):
        rom[vec] = rti & 0xFF
        rom[vec + 1] = rti >> 8
    rom[0x7FFC] = reset & 0xFF
    rom[0x7FFD] = reset >> 8
    rom[0x7FDC:0x7FE0] = bytes([0xFF, 0xFF, 0x00, 0x00])
    rom = bytearray(bytes(rom) * 4)  # 4 bancos espelhados = 128 KB
    checksum = sum(rom) & 0xFFFF
    for bank in range(4):
        base = bank * 0x8000
        rom[base + 0x7FDC] = (checksum ^ 0xFFFF) & 0xFF
        rom[base + 0x7FDD] = (checksum ^ 0xFFFF) >> 8
        rom[base + 0x7FDE] = checksum & 0xFF
        rom[base + 0x7FDF] = checksum >> 8
    return bytes(rom)


if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "retroplay-teste.sfc"
    data = build()
    with open(out, "wb") as f:
        f.write(data)
    print(f"{out}: {len(data)} bytes")
