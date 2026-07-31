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
import com.estaid.loginkit.internal.EstLog

/** 로그 출력용. `code`(ssoToken)는 저장·로그 금지이므로 값을 마스킹한다. (iOS `redactedForLog` 대칭) */
private fun redactForLog(url: String): String =
  Regex("([?&]code=)[^&]*").replace(url) { "${it.groupValues[1]}***" }

/**
 * 로그인 완료 감지(state 매칭)의 대상 state를 요청 URL에서 추출한다. (iOS `resolveInitialState` 대칭)
 *
 * 1) top-level `state` — `/user/login?...&state=` 직접 진입
 * 2) 없으면 부트스트랩(`/auth/sso-login?redirect_url=<.../user/login?...&state=...>`)의
 *    `redirect_url` 안쪽 `state` — 부트스트랩 진입 시 호스트가 top-level state를 중복으로
 *    싣지 않아도 완료를 감지할 수 있게 한다.
 */
private fun resolveInitialState(url: String): String? {
  val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
  uri.getQueryParameter("state")?.takeIf { it.isNotBlank() }?.let { return it }
  val redirect = uri.getQueryParameter("redirect_url")?.takeIf { it.isNotBlank() } ?: return null
  // redirect는 이미 1회 디코드됨(예: /user/login?...&state=https%3A%2F%2F...)
  return runCatching { Uri.parse(redirect).getQueryParameter("state") }
    .getOrNull()?.takeIf { it.isNotBlank() }
}

/**
 * SDK 내부 WebView 화면. (iOS `ESTOneWebViewController` 대칭)
 *
 * 로그인 / 마이페이지 모두 이 컴포저블을 재사용한다.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun WebViewScreen(
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
  verificationDelivery: VerificationResultDelivery? = null,
) {
  var isLoading by remember { mutableStateOf(true) }
  var webView by remember { mutableStateOf<WebView?>(null) }

  val initialState = remember(url) { resolveInitialState(url) }

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
            verificationDelivery = verificationDelivery,
          )
          webChromeClient = EstWebChromeClient(this)

          webView = this
          onWebViewCreated(this)
          EstLog.debug("open url: ${redactForLog(url)}")
          loadUrl(url)
        }
      },
      // 컴포지션에서 빠질 때(화면 전환/닫기) WebView 를 정리한다. 이게 없으면 로그인 WebView 가
      // 살아남아 자기 client(callbackUrl=app-callback)로 다른 화면(마이페이지 등)의
      // /auth/app-callback 네비게이션을 가로채 오작동(오완료·닫힘)한다.
      onRelease = { released ->
        EstLog.debug("release webview")
        released.stopLoading()
        released.webViewClient = WebViewClient()   // 우리 client 분리 → 잔여 콜백 차단
        released.removeJavascriptInterface("AndroidInterface")
        (released.parent as? ViewGroup)?.removeView(released)
        released.destroy()
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
  private val verificationDelivery: VerificationResultDelivery? = null,
) : WebViewClient() {

  /** callbackUrl 리다이렉트 체인에서 회수한 최신 ssoToken. 재발급될 수 있어 첫 값에 고정하지 않는다. */
  private var latestSsoToken: String? = null

  override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
    super.onPageStarted(view, url, favicon)
    onLoadingChange(true)
    if (url != null) EstLog.debug("page start: ${redactForLog(url)}")
  }

  override fun onPageFinished(view: WebView?, url: String?) {
    super.onPageFinished(view, url)
    onLoadingChange(false)
    if (url != null) EstLog.debug("page finished: ${redactForLog(url)}")
    CookieManager.getInstance().flush()
  }

  // 서버 302 리다이렉트는 shouldOverrideUrlLoading 을 안 거치는 경우가 있어 URL 이 샐 수 있다.
  // 히스토리 갱신 훅으로 리다이렉트 착지 URL 까지 빠짐없이 로그로 남긴다.
  override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
    super.doUpdateVisitedHistory(view, url, isReload)
    if (url != null) EstLog.debug("history: ${redactForLog(url)}")
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
    EstLog.debug("navigation: ${redactForLog(url)}")

    // Google OAuth URL → Android Chrome(비 WebView) UA 로 스왑해 embedded browser 차단 회피.
    // iOS 대칭: 일반 구글 도메인만 감지한다 (서비스별 OAuth 도메인을 하드코딩하지 않음).
    if (isGoogleLoginUrl(url)) {
      view.settings.userAgentString = buildAndroidChromeUserAgent(extraUserAgent)
    }

    // 본인인증 모드: callbackUrl 은 브릿지 미등록 시에만 여기로 리다이렉트된다
    // (`?status=...&code=<ssoToken>`). status 를 함께 파싱해 1회만 전달한다.
    if (verificationDelivery != null && !callbackUrl.isNullOrBlank() && url.startsWith(callbackUrl)) {
      EstLog.debug("[complete] verification via callbackUrl")
      val uri = runCatching { Uri.parse(url) }.getOrNull()
      verificationDelivery.fromCallback(
        status = uri?.getQueryParameter("status"),
        code = uri?.getQueryParameter("code"),
      )
      return true
    }

    // callbackUrl prefix + code 쿼리 매칭 → ssoToken 회수 (iOS 패리티)
    if (!callbackUrl.isNullOrBlank() && url.startsWith(callbackUrl)) {
      val ssoToken = runCatching { Uri.parse(url).getQueryParameter("code") }.getOrNull()
      if (!ssoToken.isNullOrBlank()) {
        latestSsoToken = ssoToken
        if (initialState.isNullOrBlank()) {
          // state 미사용 흐름: callbackUrl 자체가 종착지 — 즉시 완료.
          EstLog.debug("[complete] login via callbackUrl match → onLoginCompleted")
          onLoginCompleted(ssoToken)
          return true
        }
        // state 흐름: callbackUrl 은 서비스 세션(쿠키)을 발급하는 중간 리다이렉트다.
        // 여기서 끊으면 세션이 성립되지 않으므로 네비게이션을 통과시키고
        // state 착지에서 완료를 판정한다.
        return false
      }
    }

    // state URL prefix 매칭 → 로그인 완료 판정 (이 시점에 세션 쿠키 커밋 보장)
    if (!initialState.isNullOrBlank() && url.startsWith(initialState)) {
      EstLog.debug("[complete] login via state match → onLoginCompleted")
      onLoginCompleted(latestSsoToken.orEmpty())
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
