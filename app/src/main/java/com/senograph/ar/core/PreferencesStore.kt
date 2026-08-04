package com.senograph.ar.core

import android.content.Context
import android.content.SharedPreferences

class PreferencesStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("senograph_prefs", Context.MODE_PRIVATE)

    var referenceUri: String?
        get() = prefs.getString(KEY_REFERENCE, null)
        set(value) { prefs.edit().putString(KEY_REFERENCE, value).apply() }

    var referenceName: String?
        get() = prefs.getString(KEY_REFERENCE_NAME, null)
        set(value) { prefs.edit().putString(KEY_REFERENCE_NAME, value).apply() }

    var audioUri: String?
        get() = prefs.getString(KEY_AUDIO, null)
        set(value) { prefs.edit().putString(KEY_AUDIO, value).apply() }

    var audioName: String?
        get() = prefs.getString(KEY_AUDIO_NAME, null)
        set(value) { prefs.edit().putString(KEY_AUDIO_NAME, value).apply() }

    var controlsHidden: Boolean
        get() = prefs.getBoolean(KEY_CONTROLS_HIDDEN, false)
        set(value) { prefs.edit().putBoolean(KEY_CONTROLS_HIDDEN, value).apply() }

    var pinkTheme: Boolean
        get() = prefs.getBoolean(KEY_PINK_THEME, false)
        set(value) { prefs.edit().putBoolean(KEY_PINK_THEME, value).apply() }

    private companion object {
        const val KEY_REFERENCE = "referenceUri"
        const val KEY_REFERENCE_NAME = "referenceName"
        const val KEY_AUDIO = "audioUri"
        const val KEY_AUDIO_NAME = "audioName"
        const val KEY_CONTROLS_HIDDEN = "controlsHidden"
        const val KEY_PINK_THEME = "pinkTheme"
    }
}
