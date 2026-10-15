package id.kitachat.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import id.kitachat.R

object SoundManager {
    private var mediaPlayer: MediaPlayer? = null
    private const val TAG = "SoundManager"

    /**
     * Memutar nada telepon masuk secara berulang (looping).
     */
    fun startIncomingRingtone(context: Context) {
        playRingtone(context, R.raw.incoming_call, AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
    }

    /**
     * Memutar nada telepon keluar secara berulang (looping).
     */
    fun startOutgoingRingtone(context: Context) {
        playRingtone(context, R.raw.outgoing_ring, AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING)
    }

    /**
     * Memutar nada pesan masuk sekali putar (single play).
     */
    fun playMessageSound(context: Context) {
        try {
            MediaPlayer.create(context, R.raw.message_notification)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .build()
                )
                setOnCompletionListener { mp ->
                    mp.reset()
                    mp.release()
                }
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar suara pesan: ${e.message}", e)
        }
    }

    /**
     * Menghentikan ringtone aktif dan membersihkan sumber daya memori.
     */
    fun stopRingtone() {
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) {
                    player.stop()
                }
                player.reset()
                player.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal menghentikan ringtone: ${e.message}", e)
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Fungsi bantu privat untuk menangani inisialisasi dan pemutaran ringtone looping.
     */
    private fun playRingtone(context: Context, resId: Int, usage: Int) {
        try {
            stopRingtone()
            mediaPlayer = MediaPlayer.create(context, resId)?.apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .setUsage(usage)
                        .build()
                )
                isLooping = true
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gagal memutar ringtone: ${e.message}", e)
        }
    }
}