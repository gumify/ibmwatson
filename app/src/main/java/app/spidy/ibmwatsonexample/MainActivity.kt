package app.spidy.ibmwatsonexample

import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import app.spidy.ibmwatson.core.IbmWatson
import app.spidy.ibmwatson.data.Voice
import app.spidy.ibmwatson.utils.getVoice

class MainActivity : AppCompatActivity() {
    private lateinit var downloadManager: DownloadManager
    private lateinit var ibmWatson: IbmWatson
    private lateinit var progressBar: ProgressBar
    private lateinit var generateBtn: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT

        downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        ibmWatson = IbmWatson(this, listener).initialize()

        generateBtn = findViewById(R.id.generateBtn)
        val textView: TextView = findViewById(R.id.textView)
        progressBar = findViewById(R.id.progressBar)

        generateBtn.setOnClickListener {
            generateBtn.visibility = View.INVISIBLE
            progressBar.visibility = View.VISIBLE
            ibmWatson.generate(textView.text.toString())
        }
    }

    private val listener = object : IbmWatson.Listener {
        override fun onGenerated(id: String, cookies: String) {
            IbmWatson.VOICES.getVoice(IbmWatson.EN.VOICE_LISA)?.getSynthesizeUrl(id)?.also { url ->
                download(url, cookies)
            }
            generateBtn.visibility = View.VISIBLE
            progressBar.visibility = View.INVISIBLE
        }
        override fun onFail() {}
        override fun onCancel() {}
    }

    private fun download(url: String, cookies: String) {
        val request = DownloadManager.Request(Uri.parse(url))
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
        request.setTitle("audio")
        request.addRequestHeader("Cookie", cookies)
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "audio.mp3")
        request.setMimeType("audio/mp3")
        downloadManager.enqueue(request)
    }
}