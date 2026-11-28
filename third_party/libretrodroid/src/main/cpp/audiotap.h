/*
 *     Onzetec Games: "grampo" de áudio para os highlights.
 *     Copyright (C) 2026  Romário Marcelino
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#ifndef LIBRETRODROID_AUDIOTAP_H
#define LIBRETRODROID_AUDIOTAP_H

#include <cstdint>
#include <cstddef>
#include <mutex>
#include <vector>

namespace libretrodroid {

/**
 * Cópia do som do jogo (PCM 16 bits, estéreo, na taxa do núcleo) para quem quiser gravar.
 * O núcleo escreve (thread da emulação); o app lê periodicamente (outra thread). Guarda no
 * máximo ~2 s: o app drena a cada ~100 ms e mantém o próprio histórico.
 */
class AudioTap {
public:
    static AudioTap& getInstance();

    void setEnabled(bool enabled);
    void setSampleRate(int32_t rate);
    void push(const int16_t* data, size_t frames);

    /** Copia até [maxSamples] amostras (L,R intercaladas) para [out]; retorna quantas copiou. */
    size_t read(int16_t* out, size_t maxSamples);

    /** Horário (CLOCK_MONOTONIC, ns) da última escrita e a taxa de amostragem. */
    int64_t lastWriteNs();
    int32_t sampleRate();

private:
    AudioTap() = default;
    std::mutex mutex;
    std::vector<int16_t> ring;
    size_t readPos = 0;
    size_t writePos = 0;
    size_t count = 0;
    bool enabled = false;
    int32_t rate = 0;
    int64_t lastNs = 0;
};

} // namespace libretrodroid

#endif // LIBRETRODROID_AUDIOTAP_H
