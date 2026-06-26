/*
 * Copyright 2026 ESTAID, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.estaid.loginkit.internal.webview

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Message
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * SDK 내부 WebView 화면. (iOS `ESTOneWebViewController` 대칭)
 *
 * 로그인 / 마이페이지 모두 이 컴포저블을 재사용한다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun EstOneWebViewScreen(
  url: String,
  callbackUrl: String?,
  extraUserAgent: String?,
  inspectable: Boolean,
  debugMode: Boolean,
  onWebViewCreated: (WebView) -> Unit,
  onSnsLoginRequested: (String) -> Unit,
  onLoginCompleted: (String) -> Unit,
  onPasswordChanged: () -> Unit,
  onAccountDeleted: () -> Unit,
  onBackPressed: () -> Unit,
) {
  var isLoading by remember { mutableStateOf(true) }
  var webView by remember { mutableStateOf<WebView?>(null) }

  val initialState = remember(url) {
    runCatching { Uri.parse(url).getQueryParameter("state") }.getOrNull()
  }

  BackHandler {
    if (webView?.canGoBack() == true) webView?.goBack() else onBackPressed()
  }

  Box(modifier = Modifier.fillMaxSize()) {
    AndroidView(
      modifier = Modifier.fillMaxSize(),
      factory = { ctx ->
        if (inspectable) {
          WebView.setWebContentsDebuggingEnabled(true)
        }
        WebView(ctx).apply {
          layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
          )
          settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            textZoom = 100
            if (!extraUserAgent.isNullOrBlank()) {
              userAgentString = "$userAgentString $extraUserAgent"
            }
          }

          val currentWebView = this
          CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(currentWebView, true)
          }

          addJavascriptInterface(
            AuthJsInterface(
              onSnsLoginRequested = onSnsLoginRequested,
              onPasswordChanged = onPasswordChanged,
              onAccountDeleted = onAccountDeleted,
            ),
            "AndroidInterface",
          )

          webViewClient = EstWebViewClient(
            debugMode = debugMode,
            callbackUrl = callbackUrl,
            initialState = initialState,
            extraUserAgent = extraUserAgent,
            onLoadingChange = { isLoading = it },
            onLoginCompleted = onLoginCompleted,
          )
          webChromeClient = EstWebChromeClient(this)

          webView = this
          onWebViewCreated(this)
          loadUrl(url)
        }
      },
    )

    if (isLoading) {
      CircularProgressIndicator(
        modifier = Modifier
          .align(Alignment.Center)
          .size(48.dp),
      )
    }
  }
}

private class EstWebViewClient(
  private val debugMode: Boolean,
  private val callbackUrl: String?,
  private val initialState: String?,
  private val extraUserAgent: String?,
  private val onLoadingChange: (Boolean) -> Unit,
  private val onLoginCompleted: (String) -> Unit,
) : WebViewClient() {

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    onLoadingChange(true)
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    onLoadingChange(false)
    CookieManager.getInstance().flush()
  }

  override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
    if (debugMode) handler?.proceed() else super.onReceivedSslError(view, handler, error)
  }

  override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
    handleNavigation(view, request.url?.toString())

  @Suppress("OVERRIDE_DEPRECATION")
  override fun shouldOverrideUrlLoading(view: WebView, url: String?): Boolean =
    handleNavigation(view, url)

  private fun handleNavigation(view: WebView, navigatingUrl: String?): Boolean {
    val url = navigatingUrl ?: return false

    // Google OAuth URL → Android Chrome(비 WebView) UA 로 스왑해 embedded browser 차단 회피.
    // iOS 대칭: 일반 구글 도메인만 감지한다 (서비스별 OAuth 도메인을 하드코딩하지 않음).
    if (isGoogleLoginUrl(url)) {
      view.settings.userAgentString = buildAndroidChromeUserAgent(extraUserAgent)
    }

    // callbackUrl prefix + code 쿼리 매칭 → ssoToken 추출 후 완료
    if (!callbackUrl.isNullOrBlank() && url.startsWith(callbackUrl)) {
      val ssoToken = runCatching { Uri.parse(url).getQueryParameter("code") }.getOrNull()
      if (!ssoToken.isNullOrBlank()) {
        onLoginCompleted(ssoToken)
        return true
      }
    }

    // state URL prefix 매칭 → ssoToken 없이 완료 통지
    if (!initialState.isNullOrBlank() && url.startsWith(initialState)) {
      onLoginCompleted("")
      return false
    }

    return false
  }

  companion object {
    private val googleLoginUrlFragments = listOf(
      "accounts.google.com",
      "accounts.google.co.kr",
    )

    private fun isGoogleLoginUrl(url: String): Boolean =
      googleLoginUrlFragments.any { url.contains(it) }

    private fun buildAndroidChromeUserAgent(extra: String?): String {
      val base =
        "Mozilla/5.0 (Linux; Android 15; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) " +
          "Chrome/137.0.7151.91 Mobile Safari/537.36"
      return if (extra.isNullOrBlank()) base else "$base $extra"
    }
  }
}

/**
 * Google OAuth 는 embedded browser 판정을 위해 window.open / JS dialog 시그널을 본다.
 * 기본 WebChromeClient 로 두면 onCreateWindow 가 무시돼 "안전하지 않을 수 있습니다"로 이어질 수 있음.
 */
private class EstWebChromeClient(
  private val hostWebView: WebView,
) : WebChromeClient() {

  override fun onCreateWindow(
    view: WebView?,
    isDialog: Boolean,
    isUserGesture: Boolean,
    resultMsg: Message?,
  ): Boolean {
    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
    transport.webView = hostWebView
    resultMsg.sendToTarget()
    return true
  }

  override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    val ctx = view?.context ?: return false
    AlertDialog.Builder(ctx)
      .setMessage(message.orEmpty())
      .setPositiveButton("확인") { _, _ -> result?.confirm() }
      .setOnCancelListener { result?.cancel() }
      .show()
    return true
  }

  override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
    val ctx = view?.context ?: return false
    AlertDialog.Builder(ctx)
      .setMessage(message.orEmpty())
      .setPositiveButton("확인") { _, _ -> result?.confirm() }
      .setNegativeButton("취소") { _, _ -> result?.cancel() }
      .setOnCancelListener { result?.cancel() }
      .show()
    return true
  }

  override fun onJsPrompt(
    view: WebView?,
    url: String?,
    message: String?,
    defaultValue: String?,
    result: JsPromptResult?,
  ): Boolean {
    val ctx = view?.context ?: return false
    val input = EditText(ctx).apply { setText(defaultValue.orEmpty()) }
    AlertDialog.Builder(ctx)
      .setMessage(message.orEmpty())
      .setView(input)
      .setPositiveButton("확인") { _, _ -> result?.confirm(input.text?.toString().orEmpty()) }
      .setNegativeButton("취소") { _, _ -> result?.cancel() }
      .setOnCancelListener { result?.cancel() }
      .show()
    return true
  }
}
