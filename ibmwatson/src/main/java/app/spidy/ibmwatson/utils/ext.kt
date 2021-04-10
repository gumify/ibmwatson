package app.spidy.ibmwatson.utils

import app.spidy.ibmwatson.data.Voice

fun List<Voice>.getVoice(code: String): Voice? {
    for (v in this) {
        if (v.code == code) return v
    }
    return null
}