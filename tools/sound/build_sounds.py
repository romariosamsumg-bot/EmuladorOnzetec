#!/usr/bin/env python3
"""Gera os sons da interface e a música do menu do Onzetec Games (originais, GPL-3.0).

Tudo é sintetizado aqui (numpy) e codificado em Ogg Vorbis com o ffmpeg:
  app/src/main/res/raw/sfx_*.ogg   efeitos curtos (navegar, selecionar, voltar...)
  app/src/main/res/raw/music_menu.ogg   trilha do menu em loop (16 compassos, 96 BPM)
Uso: python3 tools/sound/build_sounds.py
"""
import subprocess
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "app/src/main/res/raw"
SR = 44100


def t(dur):
    return np.arange(int(SR * dur)) / SR


def env(n, attack=0.004, release=None, curve=6.0):
    """Envelope: ataque curto e queda exponencial."""
    x = np.arange(n) / SR
    a = np.clip(x / max(attack, 1e-4), 0, 1)
    dur = n / SR
    d = np.exp(-curve * x / dur)
    if release:
        r = np.clip((dur - x) / release, 0, 1)
        d = d * r
    return a * d


def tone(freq, dur, kind="sine", duty=0.25):
    x = t(dur)
    ph = 2 * np.pi * np.cumsum(np.broadcast_to(freq, x.shape)) / SR
    if kind == "sine":
        return np.sin(ph)
    if kind == "tri":
        return 2 / np.pi * np.arcsin(np.sin(ph))
    if kind == "pulse":
        # Pulso suavizado (soma de harmônicos): som "8 bits" sem chiado.
        out = np.zeros_like(x)
        f0 = np.atleast_1d(freq).mean()
        for k in range(1, int(min(16, SR / 2 / max(f0, 1)))):
            out += np.sin(np.pi * k * duty) / k * np.cos(k * ph)
        return out / np.max(np.abs(out) + 1e-9)
    raise ValueError(kind)


def lowpass(sig, cutoff):
    """Passa-baixas de um polo (suaviza)."""
    a = np.exp(-2 * np.pi * cutoff / SR)
    out = np.empty_like(sig)
    acc = 0.0
    for i, v in enumerate(sig):
        acc = (1 - a) * v + a * acc
        out[i] = acc
    return out


def layer(*parts):
    """Soma sons de tamanhos diferentes (o menor é completado com silêncio)."""
    n = max(len(p) for p in parts)
    out = np.zeros(n)
    for p in parts:
        out[: len(p)] += p
    return out


def seq(parts, gap=0.0):
    return np.concatenate([np.concatenate([p, np.zeros(int(SR * gap))]) for p in parts])


def write(name, sig, peak=0.8, quality=4, mono=True):
    sig = np.asarray(sig, dtype=np.float64)
    sig = sig / (np.max(np.abs(sig)) + 1e-9) * peak
    pcm = (sig * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as d:
        w = Path(d) / "a.wav"
        with wave.open(str(w), "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(SR)
            f.writeframes(pcm.tobytes())
        dst = OUT / f"{name}.ogg"
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(w), "-c:a", "libvorbis", "-q:a", str(quality), str(dst)], check=True)
    print(dst.relative_to(ROOT), dst.stat().st_size // 1024, "KB")


def blip(freq, dur, kind="pulse", duty=0.25, curve=7.0):
    s = tone(freq, dur, kind, duty)
    return s * env(len(s), curve=curve)


def sfx():
    # Navegar: "tic" curtinho e macio.
    s = layer(blip(1760, 0.035, "sine", curve=9) * 0.6, blip(3520, 0.02, "sine", curve=12) * 0.15)
    write("sfx_move", s, peak=0.45)
    # Selecionar: duas notas subindo (Mi → Si).
    write("sfx_select", seq([blip(659.3, 0.05), blip(987.8, 0.09)]), peak=0.6)
    # Voltar: duas notas descendo.
    write("sfx_back", seq([blip(880, 0.045, duty=0.5), blip(587.3, 0.08, duty=0.5)]), peak=0.5)
    # Batida na borda (não há para onde ir): grave e abafado.
    s = tone(np.linspace(180, 90, int(SR * 0.07)), 0.07, "sine") * env(int(SR * 0.07), curve=8)
    write("sfx_bump", s, peak=0.55)
    # Ligar / desligar.
    write("sfx_toggle_on", seq([blip(784, 0.04, "tri"), blip(1175, 0.07, "tri")]), peak=0.55)
    write("sfx_toggle_off", seq([blip(1175, 0.04, "tri"), blip(784, 0.07, "tri")]), peak=0.5)
    # Abrir o jogo: varredura subindo + acorde brilhante.
    sweep = tone(np.geomspace(220, 1760, int(SR * 0.28)), 0.28, "pulse", 0.125) * np.linspace(0.2, 1, int(SR * 0.28))
    chord = layer(*[blip(f, 0.45, "tri", curve=5) for f in (1046.5, 1318.5, 1568.0)]) / 3
    write("sfx_launch", seq([sweep * 0.5, chord]), peak=0.7)
    # Sucesso (salvou, print, highlight): arpejo Dó-Mi-Sol-Dó.
    write("sfx_success", seq([blip(f, 0.07, "tri") for f in (523.3, 659.3, 784.0)] + [blip(1046.5, 0.18, "tri", curve=5)]), peak=0.6)
    # Erro: dois "bzz" graves.
    write("sfx_error", seq([blip(155.6, 0.09, duty=0.5, curve=3), blip(130.8, 0.14, duty=0.5, curve=3)], gap=0.03), peak=0.55)


# ------------------------------------------------------------------ música

NOTE = {n: i for i, n in enumerate(["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"])}


def hz(name):
    """'A3' → 220 Hz."""
    n, o = name[:-1], int(name[-1])
    return 440.0 * 2 ** ((NOTE[n] + 12 * (o + 1) - 69) / 12)


def music():
    bpm = 96
    beat = 60 / bpm
    bars = 16
    total = int(SR * beat * 4 * bars)
    mix = np.zeros(total + SR * 2)  # cauda extra, somada de volta ao início (loop sem emenda)

    def add(sig, start, gain):
        i = int(start * SR)
        mix[i:i + len(sig)] += sig * gain

    # Lá menor → Fá → Dó → Sol (2 compassos cada), duas vezes.
    prog = [("A2", ["A3", "C4", "E4"]), ("F2", ["F3", "A3", "C4"]), ("C3", ["G3", "C4", "E4"]), ("G2", ["G3", "B3", "D4"])]
    for rep in range(2):
        for ci, (root, chord) in enumerate(prog):
            bar0 = (rep * 8 + ci * 2) * 4 * beat
            # Pad: acorde em triângulo levemente desafinado, ataque lento.
            dur = 8 * beat
            pad = np.zeros(int(SR * dur))
            for n in chord:
                for det in (-0.12, 0.12):
                    f = hz(n) * 2 ** (det / 12)
                    pad += tone(f, dur, "tri")
            x = t(dur)
            pad *= np.clip(x / 0.6, 0, 1) * np.clip((dur - x) / 0.5, 0, 1)
            add(lowpass(pad, 1800), bar0, 0.045)
            # Baixo: tônica nos tempos 1 e 3 (e uma passagem no 4 da 2ª volta).
            for b in range(8):
                if b % 2 == 0 or (rep == 1 and b % 4 == 3):
                    add(blip(hz(root), beat * 0.9, "tri", curve=3), bar0 + b * beat, 0.32)
            # Arpejo em semicolcheias (pulso suave), oitava acima na 2ª volta.
            notes = chord + [chord[1]]
            for k in range(32):
                n = notes[k % 4] if rep == 0 else notes[(3 - k) % 4]
                f = hz(n) * (2 if rep == 1 and k % 8 >= 4 else 1)
                add(blip(f * 2, beat / 4 * 0.95, "pulse", 0.25, curve=6), bar0 + k * beat / 4, 0.06)
            # Chimbal leve (ruído curto) nos contratempos.
            rng = np.random.default_rng(ci + rep * 4)
            for b in range(8):
                n = int(SR * 0.03)
                hat = rng.standard_normal(n) * env(n, curve=10)
                hat = hat - lowpass(hat, 5000)
                add(hat, bar0 + b * beat + beat / 2, 0.05)
    loop = mix[:total].copy()
    loop[: len(mix) - total] += mix[total:]
    write("music_menu", lowpass(loop, 7000), peak=0.7, quality=3)


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    sfx()
    music()
