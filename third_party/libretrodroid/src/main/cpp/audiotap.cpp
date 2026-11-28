/*
 *     Onzetec Games: "grampo" de áudio para os highlights.
 *     Copyright (C) 2026  Romário Marcelino
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 */

#include "audiotap.h"
#include <ctime>
#include <algorithm>

namespace libretrodroid {

static int64_t monotonicNs() {
    struct timespec ts {};
    clock_gettime(CLOCK_MONOTONIC, &ts);
    return (int64_t) ts.tv_sec * 1000000000LL + ts.tv_nsec;
}

AudioTap& AudioTap::getInstance() {
    static AudioTap instance;
    return instance;
}

void AudioTap::setEnabled(bool on) {
    std::lock_guard<std::mutex> lock(mutex);
    enabled = on;
    readPos = writePos = count = 0;
    if (!on) {
        ring.clear();
        ring.shrink_to_fit();
    }
}

void AudioTap::setSampleRate(int32_t r) {
    std::lock_guard<std::mutex> lock(mutex);
    rate = r;
    readPos = writePos = count = 0;
}

void AudioTap::push(const int16_t* data, size_t frames) {
    std::lock_guard<std::mutex> lock(mutex);
    if (!enabled || rate <= 0) return;
    if (ring.empty()) ring.assign((size_t) rate * 2 * 2, 0); // ~2 s, estéreo
    const size_t samples = frames * 2;
    const size_t cap = ring.size();
    for (size_t i = 0; i < samples; i++) {
        ring[writePos] = data[i];
        writePos = (writePos + 1) % cap;
        if (count < cap) {
            count++;
        } else {
            readPos = (readPos + 1) % cap; // cheio: descarta o mais antigo
        }
    }
    lastNs = monotonicNs();
}

size_t AudioTap::read(int16_t* out, size_t maxSamples) {
    std::lock_guard<std::mutex> lock(mutex);
    const size_t n = std::min(maxSamples, count) & ~((size_t) 1); // sempre pares (L,R)
    const size_t cap = ring.size();
    for (size_t i = 0; i < n; i++) {
        out[i] = ring[readPos];
        readPos = (readPos + 1) % cap;
    }
    count -= n;
    return n;
}

int64_t AudioTap::lastWriteNs() {
    std::lock_guard<std::mutex> lock(mutex);
    return lastNs;
}

int32_t AudioTap::sampleRate() {
    std::lock_guard<std::mutex> lock(mutex);
    return rate;
}

} // namespace libretrodroid
