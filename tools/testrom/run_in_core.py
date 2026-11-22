#!/usr/bin/env python3
"""Mini frontend libretro (ctypes) para validar a ROM de teste num núcleo real, no PC.

Uso: python3 tools/testrom/run_in_core.py <nucleo_libretro.so> <rom.sfc> [saida_dir]
Baixe o núcleo Linux em buildbot.libretro.com/nightly/linux/x86_64/latest/.
Roda um roteiro de entradas, confere cor de fundo, posição do quadrado e áudio,
e salva PNGs dos quadros (requer Pillow).
"""
import ctypes as C
import os
import struct
import sys

RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY = 9
RETRO_ENVIRONMENT_SET_PIXEL_FORMAT = 10
RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY = 31
JOY = {"B": 0, "Y": 1, "SELECT": 2, "START": 3, "UP": 4, "DOWN": 5, "LEFT": 6, "RIGHT": 7,
       "A": 8, "X": 9, "L": 10, "R": 11}


class GameInfo(C.Structure):
    _fields_ = [("path", C.c_char_p), ("data", C.c_void_p), ("size", C.c_size_t), ("meta", C.c_char_p)]


ENV = C.CFUNCTYPE(C.c_bool, C.c_uint, C.c_void_p)
VIDEO = C.CFUNCTYPE(None, C.c_void_p, C.c_uint, C.c_uint, C.c_size_t)
SAMPLE = C.CFUNCTYPE(None, C.c_int16, C.c_int16)
BATCH = C.CFUNCTYPE(C.c_size_t, C.POINTER(C.c_int16), C.c_size_t)
POLL = C.CFUNCTYPE(None)
STATE = C.CFUNCTYPE(C.c_int16, C.c_uint, C.c_uint, C.c_uint, C.c_uint)


LOGF = C.CFUNCTYPE(None, C.c_int, C.c_char_p)
LOG_CB = LOGF(lambda level, fmt: print("[núcleo]", fmt.decode(errors="replace").rstrip()) if level >= 2 else None)


def main(core_path, rom_path, out_dir):
    core = C.CDLL(os.path.abspath(core_path))
    sysdir = C.c_char_p(out_dir.encode())
    state = {"fmt": 0, "frame": None, "w": 0, "h": 0, "pitch": 0, "audio": [], "pressed": set()}

    def env(cmd, data):
        cmd &= 0xFFFF
        if cmd in (RETRO_ENVIRONMENT_GET_SYSTEM_DIRECTORY, RETRO_ENVIRONMENT_GET_SAVE_DIRECTORY):
            C.cast(data, C.POINTER(C.c_char_p))[0] = sysdir.value
            return True
        if cmd == 27:  # GET_LOG_INTERFACE (só imprime o formato)
            C.cast(data, C.POINTER(C.c_void_p))[0] = C.cast(LOG_CB, C.c_void_p)
            return True
        if cmd == RETRO_ENVIRONMENT_SET_PIXEL_FORMAT:
            state["fmt"] = C.cast(data, C.POINTER(C.c_int))[0]
            return True
        return False

    def video(data, w, h, pitch):
        if data:
            state.update(frame=C.string_at(data, pitch * h), w=w, h=h, pitch=pitch)

    def batch(data, frames):
        state["audio"].extend(data[i] for i in range(frames * 2))
        return frames

    cbs = [ENV(env), VIDEO(video), SAMPLE(lambda l, r: state["audio"].extend((l, r))), BATCH(batch),
           POLL(lambda: None),
           STATE(lambda port, dev, idx, id_: 1 if port == 0 and dev == 1 and id_ in state["pressed"] else 0)]
    core.retro_set_environment(cbs[0])
    core.retro_set_video_refresh(cbs[1])
    core.retro_set_audio_sample(cbs[2])
    core.retro_set_audio_sample_batch(cbs[3])
    core.retro_set_input_poll(cbs[4])
    core.retro_set_input_state(cbs[5])
    core.retro_load_game.restype = C.c_bool
    core.retro_init()
    rom = open(rom_path, "rb").read()
    buf = C.create_string_buffer(rom, len(rom))
    info = GameInfo(rom_path.encode(), C.cast(buf, C.c_void_p), len(rom), None)
    assert core.retro_load_game(C.byref(info)), "retro_load_game falhou"

    def pixel(x, y):
        off = y * state["pitch"] + x * 2
        v = struct.unpack_from("<H", state["frame"], off)[0]
        if state["fmt"] == 2:  # RGB565
            return ((v >> 11) & 31) * 255 // 31, ((v >> 5) & 63) * 255 // 63, (v & 31) * 255 // 31
        return ((v >> 10) & 31) * 255 // 31, ((v >> 5) & 31) * 255 // 31, (v & 31) * 255 // 31

    def save_png(name):
        try:
            from PIL import Image
        except ImportError:
            return
        img = Image.new("RGB", (state["w"], state["h"]))
        img.putdata([pixel(x, y) for y in range(state["h"]) for x in range(state["w"])])
        img.resize((state["w"] * 3, state["h"] * 3), Image.NEAREST).save(os.path.join(out_dir, name))

    def run(frames, pressed=()):
        state["pressed"] = {JOY[p] for p in pressed}
        state["audio"] = []
        for _ in range(frames):
            core.retro_run()
        peak = max((abs(s) for s in state["audio"]), default=0)
        return peak

    def find_square():
        xs, ys = [], []
        for y in range(0, state["h"]):
            for x in range(0, state["w"]):
                if pixel(x, y) == (255, 255, 255):
                    xs.append(x)
                    ys.append(y)
        return (min(xs), min(ys), max(xs) - min(xs) + 1) if xs else None

    ok = True

    def check(cond, msg):
        nonlocal ok
        print(("OK   " if cond else "FALHA") + " " + msg)
        ok = ok and cond

    peak = run(60)
    sq0 = find_square()
    check(sq0 is not None and sq0[2] == 32, f"quadrado 32x32 visível {sq0}")
    check(pixel(4, 4)[2] > 60 and pixel(4, 4)[0] < 20, f"fundo azul-escuro sem botões {pixel(4, 4)}")
    check(peak < 500, f"silêncio sem botões (pico {peak})")
    save_png("frame_repouso.png")

    peak = run(30, ("RIGHT", "B"))
    sq1 = find_square()
    check(sq1 and sq1[0] > sq0[0] + 40, f"D-pad direita move o quadrado {sq0} -> {sq1}")
    check(pixel(4, 4)[1] > 200 and pixel(4, 4)[0] < 20, f"botão B = fundo verde {pixel(4, 4)}")
    check(peak > 3000, f"botão B toca som (pico {peak})")
    save_png("frame_b_direita.png")

    run(30, ("UP", "A"))
    sq2 = find_square()
    check(sq2 and sq2[1] < sq1[1] - 40, f"D-pad cima move o quadrado {sq1} -> {sq2}")
    check(pixel(4, 4)[0] > 200 and pixel(4, 4)[1] < 20, f"botão A = fundo vermelho {pixel(4, 4)}")

    run(5)  # 1-2 quadros de latência normal do controle
    peak = run(30)
    check(peak < 500, f"som para ao soltar (pico {peak})")
    core.retro_unload_game()
    core.retro_deinit()
    print("RESULTADO:", "PASSOU" if ok else "FALHOU")
    return 0 if ok else 1


if __name__ == "__main__":
    out = sys.argv[3] if len(sys.argv) > 3 else "."
    os.makedirs(out, exist_ok=True)
    sys.exit(main(sys.argv[1], sys.argv[2], out))
