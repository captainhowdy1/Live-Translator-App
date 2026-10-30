package com.puente.translator

import com.google.mlkit.nl.translate.TranslateLanguage
import java.util.Locale

data class Lang(
    val code: String,
    val name: String,
    val native: String,
    val tag: String,
    val mlkit: String,
    val sample: String
) {
    val locale: Locale get() = Locale.forLanguageTag(tag)
}

object Languages {
    val all = listOf(
        Lang("en", "English", "English", "en-US", TranslateLanguage.ENGLISH, "Hello, this is my voice."),
        Lang("es", "Spanish", "Español", "es-US", TranslateLanguage.SPANISH, "Hola, esta es mi voz."),
        Lang("fr", "French", "Français", "fr-FR", TranslateLanguage.FRENCH, "Bonjour, voici ma voix."),
        Lang("de", "German", "Deutsch", "de-DE", TranslateLanguage.GERMAN, "Hallo, das ist meine Stimme."),
        Lang("it", "Italian", "Italiano", "it-IT", TranslateLanguage.ITALIAN, "Ciao, questa è la mia voce."),
        Lang("pt", "Portuguese", "Português", "pt-BR", TranslateLanguage.PORTUGUESE, "Olá, esta é a minha voz."),
        Lang("zh", "Chinese (Mandarin)", "中文", "zh-CN", TranslateLanguage.CHINESE, "你好，这是我的声音。"),
        Lang("ja", "Japanese", "日本語", "ja-JP", TranslateLanguage.JAPANESE, "こんにちは、これが私の声です。"),
        Lang("ko", "Korean", "한국어", "ko-KR", TranslateLanguage.KOREAN, "안녕하세요, 제 목소리입니다."),
        Lang("hi", "Hindi", "हिन्दी", "hi-IN", TranslateLanguage.HINDI, "नमस्ते, यह मेरी आवाज़ है।"),
        Lang("ar", "Arabic", "العربية", "ar-SA", TranslateLanguage.ARABIC, "مرحبًا، هذا صوتي."),
        Lang("ru", "Russian", "Русский", "ru-RU", TranslateLanguage.RUSSIAN, "Здравствуйте, это мой голос."),
        Lang("vi", "Vietnamese", "Tiếng Việt", "vi-VN", TranslateLanguage.VIETNAMESE, "Xin chào, đây là giọng của tôi."),
        Lang("pl", "Polish", "Polski", "pl-PL", TranslateLanguage.POLISH, "Cześć, to jest mój głos."),
        Lang("nl", "Dutch", "Nederlands", "nl-NL", TranslateLanguage.DUTCH, "Hallo, dit is mijn stem.")
    )
}
