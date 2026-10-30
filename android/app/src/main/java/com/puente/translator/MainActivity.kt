package com.puente.translator

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class Status { IDLE, LOADING, INTRO, LISTENING, TRANSLATING, SPEAKING, PAUSED }

    private val ui = Handler(Looper.getMainLooper())
    private var status = Status.IDLE
    private var session = 0
    private var roomMode = false
    private var myLang = Languages.all[0]
    private var theirLang = Languages.all[1]
    private var nextLang = myLang
    private var speakingLang: Lang? = null

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private val ttsCallbacks = HashMap<String, () -> Unit>()
    private var utterSeq = 0
    private var voiceOffset = 0
    private var voiceTried = false

    private var recognizer: SpeechRecognizer? = null
    private var lastPartial = ""
    private var toTheir: Translator? = null
    private var toMine: Translator? = null
    private val langId by lazy { LanguageIdentification.getClient() }
    private var lastSpoken: Pair<String, Lang>? = null
    private var pulse: ObjectAnimator? = null

    private lateinit var setupView: View
    private lateinit var sessionView: View
    private lateinit var errorText: TextView
    private lateinit var cardPhone: View
    private lateinit var cardRoom: View
    private lateinit var iconPhone: ImageView
    private lateinit var iconRoom: ImageView
    private lateinit var mySpinner: Spinner
    private lateinit var theirSpinner: Spinner
    private lateinit var maleSwitch: MaterialSwitch
    private lateinit var orb: View
    private lateinit var orbIcon: ImageView
    private lateinit var statusText: TextView
    private lateinit var partialText: TextView
    private lateinit var myChip: Button
    private lateinit var theirChip: Button
    private lateinit var transcriptScroll: ScrollView
    private lateinit var transcript: LinearLayout
    private lateinit var transcriptLabel: TextView
    private lateinit var emptyText: TextView
    private lateinit var pauseBtn: Button
    private lateinit var translateBtn: Button
    private lateinit var replayBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val root = findViewById<View>(R.id.root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        setupView = findViewById(R.id.setupView)
        sessionView = findViewById(R.id.sessionView)
        errorText = findViewById(R.id.errorText)
        cardPhone = findViewById(R.id.cardPhone)
        cardRoom = findViewById(R.id.cardRoom)
        iconPhone = findViewById(R.id.iconPhone)
        iconRoom = findViewById(R.id.iconRoom)
        mySpinner = findViewById(R.id.mySpinner)
        theirSpinner = findViewById(R.id.theirSpinner)
        maleSwitch = findViewById(R.id.maleSwitch)
        orb = findViewById(R.id.orb)
        orbIcon = findViewById(R.id.orbIcon)
        statusText = findViewById(R.id.statusText)
        partialText = findViewById(R.id.partialText)
        myChip = findViewById(R.id.myChip)
        theirChip = findViewById(R.id.theirChip)
        transcriptScroll = findViewById(R.id.transcriptScroll)
        transcript = findViewById(R.id.transcript)
        transcriptLabel = findViewById(R.id.transcriptLabel)
        emptyText = findViewById(R.id.emptyText)
        pauseBtn = findViewById(R.id.pauseBtn)
        translateBtn = findViewById(R.id.translateBtn)
        replayBtn = findViewById(R.id.replayBtn)

        val names = Languages.all.map { it.name }
        mySpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, names).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        theirSpinner.adapter = ArrayAdapter(this, R.layout.spinner_item, names).also {
            it.setDropDownViewResource(R.layout.spinner_dropdown_item)
        }
        mySpinner.setSelection(0)
        theirSpinner.setSelection(1)

        cardPhone.setOnClickListener { setMode(false) }
        cardRoom.setOnClickListener { setMode(true) }
        setMode(false)

        findViewById<Button>(R.id.tryVoiceBtn).setOnClickListener { tryVoice() }
        findViewById<Button>(R.id.startBtn).setOnClickListener { onStartClicked() }
        findViewById<Button>(R.id.endBtn).setOnClickListener { endSession(null) }
        pauseBtn.setOnClickListener { togglePause() }
        translateBtn.setOnClickListener { recognizer?.stopListening() }
        replayBtn.setOnClickListener { replay() }
        myChip.setOnClickListener { overrideLang(myLang) }
        theirChip.setOnClickListener { overrideLang(theirLang) }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (status != Status.IDLE) endSession(null) else finish()
            }
        })

        tts = TextToSpeech(this, this)
    }

    override fun onDestroy() {
        session++
        destroyRecognizer()
        tts?.stop()
        tts?.shutdown()
        toTheir?.close()
        toMine?.close()
        langId.close()
        super.onDestroy()
    }

    // ---------- Text to speech ----------

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) return
        ttsReady = true
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                ui.post { utteranceId?.let { ttsCallbacks.remove(it)?.invoke() } }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) { onDone(utteranceId) }
            override fun onError(utteranceId: String?, errorCode: Int) { onDone(utteranceId) }
        })
    }

    private fun speak(text: String, lang: Lang, then: () -> Unit) {
        val t = tts
        if (t == null || !ttsReady) {
            ui.postDelayed(then, 300)
            return
        }
        applyVoice(t, lang)
        val id = "u" + (++utterSeq)
        ttsCallbacks[id] = then
        if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) {
            ttsCallbacks.remove(id)
            ui.post(then)
        }
    }

    private fun stopSpeaking() {
        ttsCallbacks.clear()
        tts?.stop()
    }

    private fun applyVoice(t: TextToSpeech, lang: Lang) {
        t.language = lang.locale
        val wantMale = maleSwitch.isChecked
        val all: Set<Voice> = try {
            t.voices ?: emptySet()
        } catch (e: Exception) {
            emptySet()
        }
        val candidates = all.filter { v ->
            v.locale.language == lang.locale.language &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        if (candidates.isEmpty()) {
            t.setPitch(if (wantMale) 0.85f else 1f)
            return
        }
        val sorted = candidates.sortedByDescending { score(it, lang, wantMale) }
        val v = sorted[voiceOffset % sorted.size]
        t.voice = v
        t.setPitch(if (wantMale && !looksMale(v)) 0.85f else 1f)
    }

    private fun looksMale(v: Voice): Boolean {
        val n = v.name.lowercase()
        if ("female" in n) return false
        return "male" in n || KNOWN_MALE.any { n.startsWith(it) }
    }

    private fun score(v: Voice, lang: Lang, wantMale: Boolean): Int {
        var s = 0
        if (wantMale && looksMale(v)) s += 40
        if (v.locale.country.equals(lang.locale.country, ignoreCase = true)) s += 5
        if (!v.isNetworkConnectionRequired) s += 3
        s += v.quality / 100
        return s
    }

    private fun tryVoice() {
        if (status != Status.IDLE) return
        if (voiceTried) voiceOffset++
        voiceTried = true
        val mine = Languages.all[mySpinner.selectedItemPosition]
        val theirs = Languages.all[theirSpinner.selectedItemPosition]
        speak(theirs.sample, theirs) { if (status == Status.IDLE) speak(mine.sample, mine) {} }
    }

    // ---------- Session ----------

    private fun setMode(room: Boolean) {
        roomMode = room
        cardPhone.setBackgroundResource(if (!room) R.drawable.bg_card_selected else R.drawable.bg_card_unselected)
        cardRoom.setBackgroundResource(if (room) R.drawable.bg_card_selected else R.drawable.bg_card_unselected)
        iconPhone.imageTintList = ColorStateList.valueOf(color(if (!room) R.color.accent else R.color.neutral700))
        iconRoom.imageTintList = ColorStateList.valueOf(color(if (room) R.color.accent else R.color.neutral700))
    }

    private fun showError(msg: String) {
        errorText.text = msg
        errorText.visibility = View.VISIBLE
    }

    private fun onStartClicked() {
        myLang = Languages.all[mySpinner.selectedItemPosition]
        theirLang = Languages.all[theirSpinner.selectedItemPosition]
        if (myLang == theirLang) {
            showError("Pick two different languages.")
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            showError("Speech recognition isn't available on this device. Make sure the Google app is installed and enabled.")
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC)
            return
        }
        beginSession()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_MIC) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) beginSession()
            else showError("Microphone permission is needed. Allow it in Settings › Apps › Puente › Permissions.")
        }
    }

    private fun beginSession() {
        session++
        val token = session
        stopSpeaking()
        errorText.visibility = View.GONE
        setupView.visibility = View.GONE
        sessionView.visibility = View.VISIBLE
        while (transcript.childCount > 2) transcript.removeViewAt(2)
        emptyText.visibility = View.VISIBLE
        emptyText.text = "The conversation will appear here in " + myLang.name + "."
        transcriptLabel.text = myLang.name + " transcript · not saved"
        myChip.text = myLang.native
        theirChip.text = theirLang.native
        replayBtn.visibility = View.GONE
        lastSpoken = null
        partialText.text = ""
        nextLang = myLang
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setStatus(Status.LOADING)

        toTheir?.close()
        toMine?.close()
        val a = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(myLang.mlkit).setTargetLanguage(theirLang.mlkit).build()
        )
        val b = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(theirLang.mlkit).setTargetLanguage(myLang.mlkit).build()
        )
        toTheir = a
        toMine = b
        val cond = DownloadConditions.Builder().build()
        a.downloadModelIfNeeded(cond)
            .continueWithTask { b.downloadModelIfNeeded(cond) }
            .addOnSuccessListener { if (token == session) runIntro(token) }
            .addOnFailureListener {
                if (token == session) endSession(
                    "Couldn't download the language packs. Connect to the internet (Wi-Fi recommended) for the first use of each language, then try again."
                )
            }
    }

    private fun runIntro(token: Int) {
        speakingLang = theirLang
        setStatus(Status.INTRO)
        introFor(theirLang) { theirIntro ->
            if (token != session || status != Status.INTRO) return@introFor
            if (roomMode) {
                introFor(myLang) { myIntro ->
                    if (token != session || status != Status.INTRO) return@introFor
                    speakingLang = myLang
                    setStatus(Status.INTRO)
                    speak(myIntro, myLang) {
                        if (token == session && status == Status.INTRO) {
                            speakingLang = theirLang
                            speak(theirIntro, theirLang) {
                                if (status == Status.INTRO) afterSpeak(myLang, token)
                            }
                        }
                    }
                }
            } else {
                speak(theirIntro, theirLang) { if (status == Status.INTRO) afterSpeak(myLang, token) }
            }
        }
    }

    private fun introFor(lang: Lang, cb: (String) -> Unit) {
        when (lang.code) {
            "en" -> cb(INTRO_EN)
            "es" -> cb(INTRO_ES)
            else -> {
                val t = Translation.getClient(
                    TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH).setTargetLanguage(lang.mlkit).build()
                )
                t.downloadModelIfNeeded(DownloadConditions.Builder().build())
                    .continueWithTask { t.translate(INTRO_EN) }
                    .addOnCompleteListener { task ->
                        cb(if (task.isSuccessful) task.result else INTRO_EN)
                        t.close()
                    }
            }
        }
    }

    private fun afterSpeak(next: Lang, token: Int) {
        if (token != session || status == Status.PAUSED) return
        nextLang = next
        updateChips()
        ui.postDelayed({
            if (token == session && status != Status.PAUSED && status != Status.LISTENING) listen(nextLang)
        }, 600)
    }

    // ---------- Listening ----------

    private fun listen(lang: Lang) {
        if (status == Status.IDLE) return
        destroyRecognizer()
        nextLang = lang
        lastPartial = ""
        partialText.text = ""
        setStatus(Status.LISTENING)
        val token = session
        val sr = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = sr
        sr.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onPartialResults(partialResults: Bundle?) {
                if (token != session || recognizer !== sr) return
                val t = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!t.isNullOrBlank()) {
                    lastPartial = t
                    partialText.text = t
                }
            }

            override fun onResults(results: Bundle?) {
                if (token != session || recognizer !== sr || status != Status.LISTENING) return
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()
                    .orEmpty().ifEmpty { lastPartial.trim() }
                if (text.isEmpty()) {
                    relisten(token)
                    return
                }
                handleUtterance(text, lang)
            }

            override fun onError(error: Int) {
                if (token != session || recognizer !== sr || status != Status.LISTENING) return
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                        endSession("Microphone permission is needed. Allow it in Settings › Apps › Puente › Permissions.")
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                        addEntry("Notice", "Speech recognition for " + lang.name + " isn't available on this phone yet.", true)
                        togglePause()
                    }
                    else -> {
                        val partial = lastPartial.trim()
                        if (partial.isNotEmpty()) handleUtterance(partial, lang) else relisten(token)
                    }
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.tag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        }
        sr.startListening(intent)
    }

    private fun relisten(token: Int) {
        ui.postDelayed({ if (token == session && status == Status.LISTENING) listen(nextLang) }, 300)
    }

    private fun destroyRecognizer() {
        recognizer?.let {
            try {
                it.cancel()
                it.destroy()
            } catch (_: Exception) {
            }
        }
        recognizer = null
    }

    // ---------- Translating ----------

    private fun handleUtterance(text: String, heardAs: Lang) {
        val token = session
        val sr = recognizer
        ui.post { if (recognizer === sr) destroyRecognizer() }
        partialText.text = ""
        setStatus(Status.TRANSLATING)
        if (text.split(" ").size < 3) {
            translate(text, heardAs, token)
            return
        }
        langId.identifyLanguage(text)
            .addOnSuccessListener { code ->
                if (token != session) return@addOnSuccessListener
                val from = when (code) {
                    theirLang.mlkit -> theirLang
                    myLang.mlkit -> myLang
                    else -> heardAs
                }
                translate(text, from, token)
            }
            .addOnFailureListener { if (token == session) translate(text, heardAs, token) }
    }

    private fun translate(text: String, from: Lang, token: Int) {
        val mine = from == myLang
        val to = if (mine) theirLang else myLang
        val tr = (if (mine) toTheir else toMine) ?: return
        tr.translate(text)
            .addOnSuccessListener { out ->
                if (token != session || status == Status.PAUSED) return@addOnSuccessListener
                val who = if (mine) "You" else if (roomMode) "Them" else "Caller"
                addEntry(who, if (mine) text else out, mine)
                lastSpoken = out to to
                replayBtn.visibility = View.VISIBLE
                nextLang = to
                speakingLang = to
                setStatus(Status.SPEAKING)
                speak(out, to) { if (status == Status.SPEAKING) afterSpeak(to, token) }
            }
            .addOnFailureListener {
                if (token != session) return@addOnFailureListener
                addEntry("Error", "Translation failed. Please repeat that.", true)
                listen(from)
            }
    }

    // ---------- Controls ----------

    private fun togglePause() {
        if (status == Status.PAUSED) {
            listen(nextLang)
            return
        }
        if (status == Status.IDLE || status == Status.LOADING) return
        destroyRecognizer()
        stopSpeaking()
        partialText.text = ""
        setStatus(Status.PAUSED)
    }

    private fun replay() {
        val (text, lang) = lastSpoken ?: return
        val token = session
        destroyRecognizer()
        stopSpeaking()
        speakingLang = lang
        setStatus(Status.SPEAKING)
        speak(text, lang) { if (status == Status.SPEAKING) afterSpeak(nextLang, token) }
    }

    private fun overrideLang(lang: Lang) {
        nextLang = lang
        if (status == Status.LISTENING) listen(lang) else updateChips()
    }

    private fun endSession(msg: String?) {
        session++
        destroyRecognizer()
        stopSpeaking()
        stopPulse()
        status = Status.IDLE
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sessionView.visibility = View.GONE
        setupView.visibility = View.VISIBLE
        if (msg != null) showError(msg) else errorText.visibility = View.GONE
    }

    // ---------- UI ----------

    private fun setStatus(s: Status) {
        status = s
        val (c, icon, label) = when (s) {
            Status.LOADING -> Triple(R.color.neutral600, R.drawable.ic_languages, "Preparing language packs…")
            Status.INTRO -> Triple(R.color.accent, R.drawable.ic_volume, "Announcing — " + (speakingLang?.native ?: ""))
            Status.LISTENING -> Triple(R.color.accent2, R.drawable.ic_mic, "Listening — " + nextLang.native)
            Status.TRANSLATING -> Triple(R.color.neutral600, R.drawable.ic_languages, "Translating…")
            Status.SPEAKING -> Triple(R.color.accent, R.drawable.ic_volume, "Speaking — " + (speakingLang?.native ?: ""))
            Status.PAUSED -> Triple(R.color.neutral400, R.drawable.ic_pause, "Paused")
            Status.IDLE -> Triple(R.color.neutral400, R.drawable.ic_mic, "")
        }
        orb.backgroundTintList = ColorStateList.valueOf(color(c))
        orbIcon.setImageResource(icon)
        statusText.text = label
        translateBtn.visibility = if (s == Status.LISTENING) View.VISIBLE else View.GONE
        pauseBtn.text = if (s == Status.PAUSED) "Resume" else "Pause"
        pauseBtn.isEnabled = s != Status.LOADING
        updateChips()
        if (s == Status.LISTENING || s == Status.SPEAKING || s == Status.INTRO) startPulse() else stopPulse()
    }

    private fun updateChips() {
        styleChip(myChip, nextLang == myLang)
        styleChip(theirChip, nextLang == theirLang)
    }

    private fun styleChip(b: Button, on: Boolean) {
        b.backgroundTintList = ColorStateList.valueOf(color(if (on) R.color.text else R.color.neutral200))
        b.setTextColor(color(if (on) R.color.bg else R.color.neutral800))
    }

    private fun addEntry(who: String, content: String, mine: Boolean) {
        emptyText.visibility = View.GONE
        val tag = TextView(this).apply {
            text = who.uppercase()
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
            letterSpacing = 0.05f
            setTextColor(color(if (mine) R.color.accent900 else R.color.accent2_900))
            setBackgroundResource(R.drawable.bg_tag)
            backgroundTintList = ColorStateList.valueOf(color(if (mine) R.color.accent200 else R.color.accent2_200))
            setPadding(dp(10), dp(3), dp(10), dp(3))
        }
        val body = TextView(this).apply {
            text = content
            textSize = 16f
            setTextColor(color(R.color.text))
            setLineSpacing(0f, 1.2f)
            setPadding(0, dp(4), 0, 0)
        }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(14), 0, 0)
            addView(tag, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(body)
        }
        transcript.addView(box)
        transcriptScroll.post { transcriptScroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun startPulse() {
        if (pulse?.isRunning == true) return
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            orb,
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.07f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.07f)
        ).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    private fun stopPulse() {
        pulse?.cancel()
        pulse = null
        orb.scaleX = 1f
        orb.scaleY = 1f
    }

    private fun color(id: Int) = ContextCompat.getColor(this, id)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQ_MIC = 7

        private const val INTRO_EN =
            "Hello. A live translation agent has been started. Speak naturally and pause briefly when you finish; " +
                "I will then repeat what was said in the other language. This is live only, and nothing is recorded. You may begin."

        private const val INTRO_ES =
            "Hola. Se ha activado un traductor en vivo. Hable con naturalidad y haga una pausa breve al terminar; " +
                "entonces repetiré lo dicho en el otro idioma. Esto es en vivo y nada queda grabado. Puede comenzar."

        // Google text-to-speech voice IDs that are male (best effort; "Try a voice" lets you cycle).
        private val KNOWN_MALE = listOf(
            "en-us-x-iom", "en-us-x-iol", "en-us-x-tpd", "en-gb-x-gbd", "en-gb-x-rjs", "en-au-x-aud", "en-au-x-auj",
            "en-in-x-ene", "en-in-x-end", "es-us-x-esd", "es-es-x-eed", "fr-fr-x-frd", "fr-fr-x-vlf", "de-de-x-deg",
            "de-de-x-deb", "it-it-x-itd", "it-it-x-itc", "pt-br-x-ptd", "ja-jp-x-jad", "ja-jp-x-jac", "ko-kr-x-kod",
            "ko-kr-x-koc", "hi-in-x-hid", "hi-in-x-hie", "ru-ru-x-rud", "ru-ru-x-ruc", "vi-vn-x-vid", "vi-vn-x-vif",
            "pl-pl-x-bmg", "pl-pl-x-jmk", "nl-nl-x-bmh", "nl-nl-x-dma", "cmn-cn-x-ccd", "cmn-cn-x-cce", "ar-xa-x-ard",
            "ar-xa-x-are"
        )
    }
}
