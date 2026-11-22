#!/usr/bin/env python3
"""Lista as opções (retro_variable) e os tipos de controle que um núcleo libretro anuncia.

Uso: python3 tools/cores/list_core_options.py <nucleo_libretro.so> [...]
Serve para conferir as chaves usadas em ConsoleCoreOptions (modo do Neo Geo, controle de
6 botões, região, FM do Master System, overclock do SNES). Use os núcleos Linux do buildbot
(buildbot.libretro.com/nightly/linux/x86_64/latest/): as opções são as mesmas do Android.
"""
import ctypes as C
import json
import os
import sys

SET_VARIABLES = 16
SET_CONTROLLER_INFO = 35
GET_CORE_OPTIONS_VERSION = 52


class Variable(C.Structure):
    _fields_ = [("key", C.c_char_p), ("value", C.c_char_p)]


class ControllerDescription(C.Structure):
    _fields_ = [("desc", C.c_char_p), ("id", C.c_uint)]


class ControllerInfo(C.Structure):
    _fields_ = [("types", C.POINTER(ControllerDescription)), ("num_types", C.c_uint)]


ENV = C.CFUNCTYPE(C.c_bool, C.c_uint, C.c_void_p)


def inspect(path):
    core = C.CDLL(os.path.abspath(path))
    out = {"options": {}, "controllers": []}

    def env(cmd, data):
        cmd &= 0xFFFF
        if cmd == GET_CORE_OPTIONS_VERSION:
            return False  # força o formato legado, como "Descrição; v1|v2"
        if cmd == SET_VARIABLES:
            arr = C.cast(data, C.POINTER(Variable))
            i = 0
            while arr[i].key:
                out["options"][arr[i].key.decode()] = arr[i].value.decode(errors="replace")
                i += 1
            return True
        if cmd == SET_CONTROLLER_INFO:
            arr = C.cast(data, C.POINTER(ControllerInfo))
            i = 0
            while arr[i].types:
                out["controllers"].append([(arr[i].types[j].desc.decode(), arr[i].types[j].id) for j in range(arr[i].num_types)])
                i += 1
            return True
        return False

    cb = ENV(env)
    core.retro_set_environment(cb)
    core.retro_init()
    core.retro_deinit()
    return out


if __name__ == "__main__":
    result = {os.path.basename(p).replace("_libretro.so", ""): inspect(p) for p in sys.argv[1:]}
    json.dump(result, sys.stdout, ensure_ascii=False, indent=1)
