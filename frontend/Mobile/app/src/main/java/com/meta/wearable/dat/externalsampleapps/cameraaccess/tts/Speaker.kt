package com.meta.wearable.dat.externalsampleapps.cameraaccess.tts

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class Speaker (context: Context){

    private var tts: TextToSpeech? = null
    private var isReady = false
    private val pending = mutableListOf<String>()

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS){
                tts?.language = Locale.US
                isReady = true
                pending.forEach {queueSpeaking(it)}
                pending.clear()
            } else {
                Log.e("VISOR TTS", "TTS Failed: $status")
            }

        }
    }

    // this is called to use tts
    fun speak(text: String){
        if(text.isBlank()) return
        if(!isReady){
            pending.add(text)
            return
        }
        queueSpeaking(text)
    }

    // this is what is responsible for speaking wtv is passed to speak()
    private fun queueSpeaking(text: String){
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VISOR_SPEAKING")
    }

    // interupts speech w out killing engine, like a cancel btn mid sentence
    fun stop(){
        tts?.stop()
    }

    // kills whole engine, should b used in OnDestroy in MainActivity
    fun shutdown(){
        tts?.stop()
        tts?.shutdown()

    }
}