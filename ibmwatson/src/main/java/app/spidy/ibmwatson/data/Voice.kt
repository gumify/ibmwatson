package app.spidy.ibmwatson.data

data class Voice(
    val name: String,
    val code: String
) {
    fun getSynthesizeUrl(id: String): String {
        return "https://www.ibm.com/demos/live/tts-demo/api/tts/newSynthesize?voice=${code}&id=$id"
    }
}
