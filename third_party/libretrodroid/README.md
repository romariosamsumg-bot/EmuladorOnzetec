# LibretroDroid (modificado pelo Onzetec Games)

Cópia do [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) **0.14.0** de Filippo
Scognamiglio, licença **GPL-3.0** (veja `LICENSE`), compilada junto com o app.

## O que mudou

- `src/main/cpp/audiotap.h` / `audiotap.cpp` (novos): **AudioTap**, uma cópia do som do jogo
  (PCM 16 bits estéreo, na taxa do núcleo, ~2 s) com o horário da última escrita
  (`CLOCK_MONOTONIC`, o mesmo relógio do `System.nanoTime`).
- `libretrodroid.cpp`: `handleAudioCallback` também escreve no AudioTap; `afterGameLoad` informa a
  taxa de amostragem.
- `libretrodroidjni.cpp` e `LibretroDroid.java`: `setAudioTapEnabled`, `readAudioTap`,
  `audioTapLastWriteNs`, `audioTapSampleRate`.
- `rumble.h`, `utils/javautils.h`: `#include <functional>` (exigido pelo NDK 27).
- Submódulos reduzidos ao necessário: `oboe` (só `include/`, `src/` e `CMakeLists.txt`) e
  `libretro-common` (só `include/` e os 5 arquivos `.c` usados).

O app usa o AudioTap para gravar os **highlights com som** (`HighlightRecorder`).

## Compilar

Precisa do NDK `27.2.12479018` e do CMake `3.22.1` (o Gradle compila sozinho junto com o app):

```bash
sdkmanager "ndk;27.2.12479018" "cmake;3.22.1"
./gradlew :libretrodroid:assembleRelease
```
