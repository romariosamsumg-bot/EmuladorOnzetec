#!/usr/bin/env python3
"""Carrega um romset num núcleo libretro de arcade (ex.: MAME 2000) e roda alguns quadros.

Uso: python3 tools/cores/load_romset.py <nucleo_libretro.so> <romset.zip> [quadros]
Serve para conferir onde o núcleo procura o romset "pai" e a BIOS (a mesma pasta do jogo).
Para testar sem ROMs reais, gere romsets sintéticos com forge_romset.py (CRCs do DAT).
"""
import ctypes as C
import os
import sys

ENV = C.CFUNCTYPE(C.c_bool, C.c_uint, C.c_void_p)
VIDEO = C.CFUNCTYPE(None, C.c_void_p, C.c_uint, C.c_uint, C.c_size_t)
SAMPLE = C.CFUNCTYPE(None, C.c_int16, C.c_int16)
BATCH = C.CFUNCTYPE(C.c_size_t, C.POINTER(C.c_int16), C.c_size_t)
POLL = C.CFUNCTYPE(None)
STATE = C.CFUNCTYPE(C.c_int16, C.c_uint, C.c_uint, C.c_uint, C.c_uint)


class GameInfo(C.Structure):
    _fields_ = [("path", C.c_char_p), ("data", C.c_void_p), ("size", C.c_size_t), ("meta", C.c_char_p)]


class Variable(C.Structure):
    _fields_ = [("key", C.c_char_p), ("value", C.c_char_p)]


VARS = {b"mame2000-skip_disclaimer": b"enabled", b"mame2000-show_gameinfo": b"disabled"}


def run(core_path, rom, frames=120):
    core = C.CDLL(os.path.abspath(core_path))
    sysdir = C.c_char_p(os.path.dirname(os.path.abspath(rom)).encode())
    state = {"frames": 0, "w": 0, "h": 0}
    keep = []

    def env(cmd, data):
        cmd &= 0xFFFF
        if cmd in (9, 31):  # GET_SYSTEM_DIRECTORY, GET_SAVE_DIRECTORY
            C.cast(data, C.POINTER(C.c_char_p))[0] = sysdir.value
            return True
        if cmd == 15:  # GET_VARIABLE
            v = C.cast(data, C.POINTER(Variable))[0]
            val = VARS.get(v.key)
            if val:
                buf = C.c_char_p(val); keep.append(buf)
                C.cast(data, C.POINTER(Variable))[0].value = buf.value
                return True
            return False
        if cmd == 10:  # SET_PIXEL_FORMAT
            return True
        return False

    cbs = [ENV(env), VIDEO(lambda d, w, h, p: state.update(frames=state["frames"] + (1 if d else 0), w=w, h=h)),
           SAMPLE(lambda l, r: None), BATCH(lambda d, n: n), POLL(lambda: None), STATE(lambda *a: 0)]
    core.retro_set_environment(cbs[0])
    core.retro_init()
    core.retro_set_video_refresh(cbs[1]); core.retro_set_audio_sample(cbs[2]); core.retro_set_audio_sample_batch(cbs[3])
    core.retro_set_input_poll(cbs[4]); core.retro_set_input_state(cbs[5])
    info = GameInfo(os.path.abspath(rom).encode(), None, 0, None)
    core.retro_load_game.restype = C.c_bool
    ok = core.retro_load_game(C.byref(info))
    if ok:
        for _ in range(frames):
            core.retro_run()
        core.retro_unload_game()
    core.retro_deinit()
    return ok, state


if __name__ == "__main__":
    ok, st = run(sys.argv[1], sys.argv[2], int(sys.argv[3]) if len(sys.argv) > 3 else 120)
    print(f"carregou={ok} quadros={st['frames']} tela={st['w']}x{st['h']}")
    sys.exit(0 if ok else 1)
