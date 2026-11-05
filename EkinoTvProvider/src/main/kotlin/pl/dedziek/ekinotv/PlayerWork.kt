// Compatible with MoonGetter's public Filemoon/Byse initialization function (MIT).
// Copyright (c) 2024 Xavier Alexander Torres Calderón. Full notice: src/main/resources/META-INF/BYSE-LICENSE.txt.
package pl.dedziek.ekinotv

import kotlinx.coroutines.yield

internal object PlayerWork {
    const val NOTICE = """MIT License

Copyright (c) 2024 Xavier Alexander Torres Calderón

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE."""

    suspend fun answer(nonce: String, bits: Int, attempts: Int = 1_000_000): String? {
        require(nonce.length in 1..256 && nonce.all { it.code in 0..127 } && bits in 0..20 && attempts in 1..1_000_000)
        for (candidate in 0 until attempts) {
            if ((candidate and 511) == 0) yield()
            val result = digest("$nonce:$candidate")
            var zeros = 0
            for (word in result) { zeros += word.countLeadingZeroBits(); if (word != 0) break }
            if (zeros >= bits) return candidate.toString()
        }
        return null
    }

    private class Mixer {
        val state = intArrayOf(0x6a09e667, 0xbb67ae85.toInt(), 0x3c6ef372, 0xa54ff53a.toInt())
        fun round() {
            state[0] += state[1]; state[3] = rotate(state[3] xor state[0], 16)
            state[2] += state[3]; state[1] = rotate(state[1] xor state[2], 12)
            state[0] += state[1]; state[3] = rotate(state[3] xor state[0], 8)
            state[2] += state[3]; state[1] = rotate(state[1] xor state[2], 7)
        }
    }

    private fun rotate(value: Int, distance: Int) = (value shl distance) or (value ushr (32 - distance))
    private fun digest(input: String): IntArray {
        val mixer = Mixer(); val s = mixer.state
        for (char in input) { s[0] = rotate(s[0] + (char.code and 255), 7); mixer.round() }
        repeat(8) { mixer.round() }
        val memory = IntArray(512) { mixer.round(); s[0] xor s[2] }
        repeat(2) {
            for (i in memory.indices) {
                memory[i] = rotate(memory[i] + memory[memory[i] and 511], 13) xor
                    (memory[(i + 1) and 511] * 0x9e3779b1.toInt())
                s[0] = s[0] xor memory[i]; mixer.round()
            }
        }
        return IntArray(8) { block ->
            mixer.round()
            var word = s[0]
            for (i in block * 64 until (block + 1) * 64) word = rotate(word + memory[i], 5) xor (memory[i] * 0x85ebca77.toInt())
            word xor s[2]
        }
    }
}
