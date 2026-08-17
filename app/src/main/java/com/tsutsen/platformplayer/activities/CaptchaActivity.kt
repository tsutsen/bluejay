package com.tsutsen.platformplayer.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.widget.Button
import com.tsutsen.platformplayer.R
import com.tsutsen.platformplayer.api.media.platforms.js.SourceCaptchaData
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginCaptchaConfig
import com.tsutsen.platformplayer.api.media.platforms.js.SourcePluginConfig
import com.tsutsen.platformplayer.logging.Logger
import com.tsutsen.platformplayer.others.CaptchaWebViewClient
import com.tsutsen.platformplayer.states.StateApp
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.lang.Exception

/**
 * Captcha solving activity using WebView.
 * Shows a captcha page and extracts cookies/headers when the captcha is solved.
 */
class CaptchaActivity : Activity() {
    private lateinit var _webView: WebView;
    private lateinit var _buttonClose: Button;
    private var _pluginConfig: SourcePluginConfig? = null;
    private var _captchaConfig: SourcePluginCaptchaConfig? = null;
    private var _extraUrl: String? = null;
    private var _extraBody: String? = null;

    companion object {
        private const val TAG = "CaptchaActivity";

        fun showCaptcha(
            context: Context,
            config: SourcePluginConfig,
            url: String,
            body: String?,
            callback: (SourceCaptchaData?) -> Unit
        ) {
            val captchaConfig = config.captcha
                ?: throw IllegalStateException("Plugin has no captcha support");

            val intent = Intent(context, CaptchaActivity::class.java).apply {
                putExtra("plugin", Json.encodeToString(config))
                putExtra("url", url)
                putExtra("body", body)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            _callback = callback
            context.startActivity(intent)
        }

        private var _callback: ((SourceCaptchaData?) -> Unit)? = null
    }

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(StateApp.instance.getLocaleContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_captcha);
        setNavigationBarColorAndIcons();

        _buttonClose = findViewById(R.id.button_close);
        _buttonClose.setOnClickListener { finish(); };

        _webView = findViewById(R.id.web_view);
        _webView.settings.javaScriptEnabled = true;
        CookieManager.getInstance().setAcceptCookie(true);

        _pluginConfig = intent.getStringExtra("plugin")?.let {
            Json.decodeFromString<SourcePluginConfig>(it)
        };

        _captchaConfig = _pluginConfig?.captcha
            ?: throw IllegalStateException("Plugin has no captcha support");

        _extraUrl = intent.getStringExtra("url");
        _extraBody = intent.getStringExtra("body");

        if (_captchaConfig!!.userAgent != null)
            _webView.settings.userAgentString = _captchaConfig!!.userAgent;
        val capturedUserAgent = _webView.settings.userAgentString;
        _webView.settings.useWideViewPort = true;
        _webView.settings.loadWithOverviewMode = true;

        val webViewClient = CaptchaWebViewClient(_pluginConfig!! ?: throw IllegalStateException("No config"), capturedUserAgent);
        webViewClient.onCaptchaFinished.subscribe { captcha ->
            _callback?.let {
                _callback = null;
                it(captcha);
            }
            finish();
        };
        _webView.settings.domStorageEnabled = true;
        _webView.webViewClient = webViewClient;

        val captchaUrl = _captchaConfig?.captchaUrl;
        if(captchaUrl != null)
            _webView.loadUrl(captchaUrl);
        else if(_extraUrl != null && _extraBody != null)
            _webView.loadDataWithBaseURL(_extraUrl!!, _extraBody!!, "text/html", "utf-8", null);
        else if(_extraUrl != null)
            _webView.loadUrl(_extraUrl!!);
        else throw IllegalStateException("No valid captcha info provided");
    }

    override fun finish() {
        _webView.loadUrl("about:blank");
        _callback?.let {
            _callback = null;
            it(null);
        }
        super.finish();
    }

    private fun setNavigationBarColorAndIcons() {
        // Stub: restore theme colors
        window?.navigationBarColor = android.graphics.Color.TRANSPARENT
    }
}
