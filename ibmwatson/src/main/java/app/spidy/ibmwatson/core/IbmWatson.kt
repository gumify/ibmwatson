package app.spidy.ibmwatson.core

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.webkit.*
import app.spidy.hiper.Hiper
import app.spidy.hiper.utils.mix
import app.spidy.ibmwatson.data.Voice
import app.spidy.kotlinutils.async
import app.spidy.kotlinutils.debug
import app.spidy.kotlinutils.onUiThread
import app.spidy.kotlinutils.sleep


class IbmWatson(private val context: Context, private val listener: Listener) {

    private lateinit var webView: WebView
    private var isLoaded = false
    private val hiper = Hiper.getInstance()
    private var isCanceled = false
    private var isError = false
    private var text: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize(): IbmWatson {
        debug("STATUS: initialize...")

        webView = WebView(context)
        webView.settings.apply {
            layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
            javaScriptEnabled = true
            builtInZoomControls = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
            databaseEnabled = true
            domStorageEnabled = true
            setGeolocationEnabled(true)
            useWideViewPort = true
            mediaPlaybackRequiresUserGesture = false
        }
        webView.setInitialScale(100)
        webView.webChromeClient = object : WebChromeClient() {

        }
        webView.requestFocus()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                debug("STATUS: Loading page...")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoaded = true
                if (url != null && url.contains("ibm.com")) {
                    generateVoice(text)
                }
                debug("STATUS: Page loaded")
            }

            override fun onLoadResource(view: WebView?, url: String?) {
                if (url != null && url.contains("newSynthesize?")) {
                    debug("STATUS: $url")
                    checkUrl(url)
                }
                super.onLoadResource(view, url)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (!isError) {
                    debug("ERROR: onReceivedError. $error")
                    listener.onFail()
                    isError = true
                }
            }
        }

        return this
    }

    private fun parseCookies(s: String): HashMap<String, Any> {
        val map = mix()
        val nodes = s.split("; ")
        for (n in nodes) {
            val parts = n.split("=").toMutableList()
            val k = parts.removeAt(0)
            val v = parts.joinToString("=")
            map[k] = v
        }
        return map
    }

    fun cancel() {
        isCanceled = true
    }

    private fun checkUrl(url: String) {
        val uri = Uri.parse(url)
        val id = uri.getQueryParameter("id")
        if (id == null) {
            listener.onFail()
            return
        }
        val userAgent = webView.settings.userAgentString
        async {
            while (true) {
                if (isCanceled) break
                if (isError) break
                try {
                    val cookies = parseCookies(CookieManager.getInstance().getCookie(url))
                    debug("STATUS: (cookies) $cookies")
                    val r = hiper.get(url, cookies = cookies, isStream = true)
                    debug("STATUS: (statusCode) ${r.statusCode}")
                    if (r.isSuccessful) {
                        r.stream?.close()
                        r.close()
                        break
                    }
                    r.stream?.close()
                    r.close()
                } catch (e: Exception) {
                    debug(e)
                }
                sleep(100)
            }
            onUiThread {
                if (isCanceled) {
                    listener.onCancel()
                } else {
                    listener.onGenerated(id, CookieManager.getInstance().getCookie(url))
                }
                clearCookies()
                webView.loadData("<pre>blank</pre>", "text/html", "utf-8")
            }
        }
    }

    private fun generateVoice(s: String) {
        val query = s.replace("\n", "")
        async {
            while (!isLoaded) sleep(100)
            onUiThread {
                webView.evaluateJavascript("""
                    (function() {
                        document.querySelector("#text-area").value = `$s`;
                        document.querySelector("#text-area").focus();
                        document.querySelector("audio").muted = true;
                    })();
                """.trimIndent()) {
                    val chars = " ".toCharArray()
                    val charMap: KeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
                    val events = charMap.getEvents(chars)
                    val delEvent = KeyEvent(0, 0, 0, KeyEvent.KEYCODE_DEL, 0, 0, 0, 0, KeyEvent.KEYCODE_ENDCALL)
                    webView.dispatchKeyEvent(events[0])
                    async {
                        sleep(1000)
                        onUiThread { webView.dispatchKeyEvent(delEvent) }
                        sleep(1000)
                        onUiThread {
                            webView.evaluateJavascript("""
                                (function() {
                                    document.querySelector(".play-btn").click();
                                })();
                            """.trimIndent()) {}
                        }
                    }
                }
            }
        }
    }

    fun generate(s: String) {
        isCanceled = false
        isError = false
        text = s
        webView.loadUrl("https://www.ibm.com/demos/live/tts-demo/self-service/home")
    }

    private fun clearCookies() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
            debug("Using clearCookies code for API >=" + Build.VERSION_CODES.LOLLIPOP_MR1.toString())
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } else {
            debug("Using clearCookies code for API <" + Build.VERSION_CODES.LOLLIPOP_MR1.toString())
            val cookieSyncMngr = CookieSyncManager.createInstance(context)
            cookieSyncMngr.startSync()
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookie()
            cookieManager.removeSessionCookie()
            cookieSyncMngr.stopSync()
            cookieSyncMngr.sync()
        }
    }


    companion object {
        val VOICES: List<Voice>
            get() = EN.voices + AR.voices + CN.voices + NL.voices + FR.voices + DE.voices + IT.voices + JP.voices + KR.voices + BR.voices
    }


    interface Listener {
        fun onGenerated(id: String, cookies: String)
        fun onFail()
        fun onCancel()
    }

    object EN {
        const val NAME = "English"
        const val VOICE_OLIVIA = "en-US_OliviaV3Voice"
        const val VOICE_MICHAEL = "en-US_MichaelV3Voice"
        const val VOICE_LISA = "en-US_LisaV3Voice"
        const val VOICE_KEVIN = "en-US_KevinV3Voice"
        const val VOICE_HENRY = "en-US_HenryV3Voice"
        const val VOICE_EMILY = "en-US_EmilyV3Voice"
        const val VOICE_ALLISON = "en-US_AllisonV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Olivia", VOICE_OLIVIA),
                Voice("Michael", VOICE_MICHAEL),
                Voice("Lisa", VOICE_LISA),
                Voice("Kevin", VOICE_KEVIN),
                Voice("Henry", VOICE_HENRY),
                Voice("Emily", VOICE_EMILY),
                Voice("Allison", VOICE_ALLISON)
            )
    }

    object AR {
        const val NAME = "Arabic"
        const val VOICE_OMAR = "ar-AR_OmarVoice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Omar", VOICE_OMAR)
            )
    }

    object CN {
        const val NAME = "Chinese"
        const val VOICE_ZHANG_JING = "zh-CN_ZhangJingVoice"
        const val VOICE_WANG_WEI = "zh-CN_WangWeiVoice"
        const val VOICE_LI_NA = "zh-CN_LiNaVoice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Zhang Jing", VOICE_ZHANG_JING),
                Voice("Wang Wei", VOICE_WANG_WEI),
                Voice("Li Na", VOICE_LI_NA)
            )
    }

    object NL {
        const val NAME = "Dutch"
        const val VOICE_EMMA = "nl-NL_EmmaVoice"
        const val VOICE_LIAM = "nl-NL_LiamVoice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Emma", VOICE_EMMA),
                Voice("Liam", VOICE_LIAM)
            )
    }

    object FR {
        const val NAME = "French"
        const val VOICE_RENEE = "fr-FR_ReneeV3Voice"
        const val VOICE_NICOLAS = "fr-FR_NicolasV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Renee", VOICE_RENEE),
                Voice("Nicolas", VOICE_NICOLAS)
            )
    }

    object DE {
        const val NAME = "German"
        const val VOICE_DIETER = "de-DE_DieterV3Voice"
        const val VOICE_ERIKA = "de-DE_ErikaV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Dieter", VOICE_DIETER),
                Voice("Erika", VOICE_ERIKA)
            )
    }

    object IT {
        const val NAME = "Italian"
        const val VOICE_FRANCESCA = "it-IT_FrancescaV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Francesca", VOICE_FRANCESCA)
            )
    }

    object JP {
        const val NAME = "Japanese"
        const val VOICE_EMI = "ja-JP_EmiV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Emi", VOICE_EMI)
            )
    }

    object KR {
        const val NAME = "Korean"
        const val VOICE_YUNA = "ko-KR_YunaVoice"
        const val VOICE_YOUNGMI = "ko-KR_YoungmiVoice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Yuna", VOICE_YUNA),
                Voice("Youngmi", VOICE_YOUNGMI)
            )
    }

    object BR {
        const val NAME = "Portuguese"
        const val VOICE_ISABELA = "pt-BR_IsabelaV3Voice"
        val voices: List<Voice>
            get() = listOf(
                Voice("Isabela", VOICE_ISABELA)
            )
    }
}