package de.protokollnull.agent.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

/**
 * Prozedural erzeugte, eigene Ambient-Musik (keine Fremdaufnahmen):
 * langsame Moll-Flächen, sanftes Pulsieren und ein leiser Radarton.
 */
class AmbientMusic {
    @Volatile private var running = false
    @Volatile private var volume = 0.35f
    private var thread: Thread? = null

    fun setVolume(percent: Int) { volume = (percent.coerceIn(0, 100) / 100f) * 0.5f }

    fun start(percent: Int) {
        setVolume(percent)
        if (running || percent <= 0) return
        running = true
        thread = Thread({ loop() }, "pn-ambient").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    fun stop() {
        running = false
        thread?.join(300)
        thread = null
    }

    private fun loop() {
        val rate = 22050
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val track = runCatching {
            AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes((minBuf * 2).coerceAtLeast(8192))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        }.getOrNull() ?: run { running = false; return }
        // Akkorde (Grundtöne in Hz): Am – F – C – Em
        val chords = arrayOf(
            floatArrayOf(110f, 130.81f, 164.81f), floatArrayOf(87.31f, 110f, 130.81f),
            floatArrayOf(98f, 130.81f, 164.81f), floatArrayOf(82.41f, 123.47f, 164.81f)
        )
        val chordLen = rate * 9
        val buf = ShortArray(2048)
        var n = 0L
        val phases = DoubleArray(6)
        var gain = 0f
        runCatching { track.play() }
        while (running) {
            for (i in buf.indices) {
                val pos = (n % (chordLen * chords.size)).toInt()
                val idx = pos / chordLen
                val within = (pos % chordLen).toFloat() / chordLen
                val next = (idx + 1) % chords.size
                val blend = if (within > 0.85f) (within - 0.85f) / 0.15f else 0f
                var sample = 0.0
                for (v in 0 until 3) {
                    val f = chords[idx][v] * (1 - blend) + chords[next][v] * blend
                    phases[v] += 2 * PI * f / rate
                    phases[v + 3] += 2 * PI * (f * 2.004) / rate
                    sample += sin(phases[v]) * 0.22 + sin(phases[v + 3]) * 0.07
                }
                val t = n.toDouble() / rate
                val lfo = 0.75 + 0.25 * sin(2 * PI * 0.08 * t)
                // leiser Radar-Ping alle 6 Sekunden
                val pingT = t % 6.0
                val ping = if (pingT < 0.5) sin(2 * PI * 880 * pingT) * (1 - pingT / 0.5) * (1 - pingT / 0.5) * 0.06 else 0.0
                gain += (volume - gain) * 0.0005f
                val v = ((sample * lfo + ping) * gain * 32767 * 0.6).coerceIn(-32767.0, 32767.0)
                buf[i] = v.toInt().toShort()
                n++
            }
            if (track.write(buf, 0, buf.size) < 0) break
        }
        runCatching { track.stop(); track.release() }
    }
}
