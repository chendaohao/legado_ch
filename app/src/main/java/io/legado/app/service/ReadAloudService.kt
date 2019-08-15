package io.legado.app.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.support.v4.media.session.MediaSessionCompat
import io.legado.app.R
import io.legado.app.base.BaseService
import io.legado.app.receiver.MediaButtonIntentReceiver
import io.legado.app.utils.toast
import kotlinx.coroutines.launch
import java.util.*

class ReadAloudService : BaseService(), TextToSpeech.OnInitListener, AudioManager.OnAudioFocusChangeListener {

    companion object {
        val tag = ReadAloudService::class.java.simpleName
        var isRun = false
        fun paly(context: Context, title: String, body: String) {

        }

        fun pause(context: Context) {
            if (isRun) {
                val intent = Intent(context, ReadAloudService::class.java)
                intent.action = "pause"
                context.startService(intent)
            }
        }

        fun resume(context: Context) {
            if (isRun) {
                val intent = Intent(context, ReadAloudService::class.java)
                intent.action = "resume"
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            if (isRun) {
                val intent = Intent(context, ReadAloudService::class.java)
                intent.action = "stop"
                context.startService(intent)
            }
        }
    }

    private var textToSpeech: TextToSpeech? = null
    private var ttsIsSuccess: Boolean = false
    private lateinit var audioManager: AudioManager
    private lateinit var mFocusRequest: AudioFocusRequest
    private var mediaSessionCompat: MediaSessionCompat? = null

    override fun onCreate() {
        super.onCreate()
        isRun = true
        textToSpeech = TextToSpeech(this, this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initFocusRequest()
        initMediaSession()

    }

    override fun onDestroy() {
        super.onDestroy()
        isRun = false
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.action?.let { action ->
            when (action) {
                "play" -> {

                }
                "pause" -> {

                }
                "resume" -> {

                }
                "stop" -> {
                    stopSelf()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onInit(status: Int) {
        launch {
            if (status == TextToSpeech.SUCCESS) {
                val result = textToSpeech?.setLanguage(Locale.CHINA)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    toast(R.string.tts_fix)
                    toTTSSetting()
                } else {
                    textToSpeech?.setOnUtteranceProgressListener(TTSUtteranceListener())
                    ttsIsSuccess = true
                }
            } else {
                toast(R.string.tts_init_failed)
            }
        }
    }

    /**
     * 初始化MediaSession
     */
    private fun initMediaSession() {
        val mComponent = ComponentName(packageName, MediaButtonIntentReceiver::class.java.name)
        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON)
        mediaButtonIntent.component = mComponent
        val mediaButtonReceiverPendingIntent = PendingIntent.getBroadcast(
            this, 0,
            mediaButtonIntent, PendingIntent.FLAG_CANCEL_CURRENT
        )

        mediaSessionCompat = MediaSessionCompat(this, tag, mComponent, mediaButtonReceiverPendingIntent)
        mediaSessionCompat?.setCallback(object : MediaSessionCompat.Callback() {
            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                return MediaButtonIntentReceiver.handleIntent(this@ReadAloudService, mediaButtonEvent)
            }
        })
        mediaSessionCompat?.setMediaButtonReceiver(mediaButtonReceiverPendingIntent)
    }

    private fun initFocusRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mPlaybackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()
            mFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(mPlaybackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(this)
                .build()
        }
    }

    /**
     * @return 音频焦点
     */
    private fun requestFocus(): Boolean {
        val request: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioManager.requestAudioFocus(mFocusRequest)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                this,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }
        return request == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                // 重新获得焦点,  可做恢复播放，恢复后台音量的操作
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                // 永久丢失焦点除非重新主动获取，这种情况是被其他播放器抢去了焦点，  为避免与其他播放器混音，可将音乐暂停
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // 暂时丢失焦点，这种情况是被其他应用申请了短暂的焦点，可压低后台音量
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // 短暂丢失焦点，这种情况是被其他应用申请了短暂的焦点希望其他声音能压低音量（或者关闭声音）凸显这个声音（比如短信提示音），
            }
        }
    }

    private fun toTTSSetting() {
        //跳转到文字转语音设置界面
        try {
            val intent = Intent()
            intent.action = "com.android.settings.TTS_SETTINGS"
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
        } catch (ignored: Exception) {
            toast(R.string.tip_cannot_jump_setting_page)
        }
    }

    /**
     * 朗读监听
     */
    private inner class TTSUtteranceListener : UtteranceProgressListener() {

        override fun onStart(s: String) {

        }

        override fun onDone(s: String) {

        }

        override fun onError(s: String) {

        }

        override fun onRangeStart(utteranceId: String, start: Int, end: Int, frame: Int) {
            super.onRangeStart(utteranceId, start, end, frame)

        }
    }

}