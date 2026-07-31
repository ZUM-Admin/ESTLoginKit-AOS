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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.internal.dto.SnsLoginBridge
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.LoginPlatform
import kotlinx.coroutines.launch

/**
 * WebView 로그인 호스트 액티비티. (iOS `ESTOneWebViewController` + 호스트 present 역할 통합)
 *
 * 웹에서 SNS 네이티브 로그인을 요청하면 [EstLoginManager.socialLogin] 으로 처리하고,
 * 결과를 JS 콜백으로 다시 웹에 전달한다. callbackUrl 의 `code` 가 ssoToken 으로
 * 회수되면 RESULT_OK + ssoToken extra 로 종료한다.
 */
internal class WebViewActivity : ComponentActivity() {

  companion object {
    const val EXTRA_LOGIN_URL = "extra_login_url"
    const val EXTRA_CALLBACK_URL = "extra_callback_url"
    const val EXTRA_EXTRA_USER_AGENT = "extra_extra_user_agent"
    const val EXTRA_INSPECTABLE = "extra_inspectable"
    const val EXTRA_DEBUG_MODE = "extra_debug_mode"
    const val RESULT_SSO_TOKEN = "result_sso_token"

    fun createIntent(
      context: Context,
      loginUrl: String,
      callbackUrl: String? = null,
      extraUserAgent: String? = null,
      inspectable: Boolean = false,
      debugMode: Boolean = false,
    ): Intent = Intent(context, WebViewActivity::class.java).apply {
      putExtra(EXTRA_LOGIN_URL, loginUrl)
      if (!callbackUrl.isNullOrBlank()) putExtra(EXTRA_CALLBACK_URL, callbackUrl)
      if (!extraUserAgent.isNullOrBlank()) putExtra(EXTRA_EXTRA_USER_AGENT, extraUserAgent)
      putExtra(EXTRA_INSPECTABLE, inspectable)
      putExtra(EXTRA_DEBUG_MODE, debugMode)
    }
  }

  private var webViewRef: WebView? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: run {
      EstLog.error("Login URL not provided")
      setResult(Activity.RESULT_CANCELED)
      finish()
      return
    }
    val callbackUrl = intent.getStringExtra(EXTRA_CALLBACK_URL)
    val extraUserAgent = intent.getStringExtra(EXTRA_EXTRA_USER_AGENT)
    val inspectable = intent.getBooleanExtra(EXTRA_INSPECTABLE, false)
    val debugMode = intent.getBooleanExtra(EXTRA_DEBUG_MODE, false)

    setContent {
      WebViewScreen(
        url = loginUrl,
        callbackUrl = callbackUrl,
        extraUserAgent = extraUserAgent,
        inspectable = inspectable,
        debugMode = debugMode,
        onWebViewCreated = { webView ->
          webViewRef = webView
          EstLoginManager.getConfig()?.onWebViewCreated?.invoke(webView)
        },
        onSnsLoginRequested = ::handleSnsLoginRequest,
        onLoginCompleted = { ssoToken ->
          val data = Intent().apply {
            if (ssoToken.isNotBlank()) putExtra(RESULT_SSO_TOKEN, ssoToken)
          }
          setResult(Activity.RESULT_OK, data)
          finish()
        },
        onPasswordChanged = { EstLoginManager.getConfig()?.onPasswordChanged?.invoke() },
        onAccountDeleted = { EstLoginManager.getConfig()?.onAccountDeleted?.invoke() },
        onBackPressed = {
          setResult(Activity.RESULT_CANCELED)
          finish()
        },
      )
    }
  }

  private fun handleSnsLoginRequest(message: String) {
    val parsed = SnsLoginBridge.parseRequest(message).getOrElse {
      EstLog.error("Failed to parse SNS login request", it)
      return
    }
    val platform = LoginPlatform.from(parsed.provider)
    if (platform == null) {
      // 구글/애플 등은 네이티브 미지원 → 웹 OAuth 경로 사용. 에러 콜백 통지.
      val payload = SnsLoginBridge.encodeError(
        provider = parsed.provider,
        code = "unsupported_provider",
        message = "${parsed.provider} native login is not supported. Use WebView OAuth path.",
      )
      runOnUiThread { evaluateJs(SnsLoginBridge.errorScript(payload)) }
      return
    }

    lifecycleScope.launch {
      val script = try {
        val result = EstLoginManager.socialLogin(this@WebViewActivity, platform)
        SnsLoginBridge.successScript(SnsLoginBridge.encodeSuccess(platform, result))
      } catch (e: AuthError) {
        val code = if (e is AuthError.Cancelled) "cancelled" else "sdk_error"
        SnsLoginBridge.errorScript(
          SnsLoginBridge.encodeError(platform.rawValue, code, e.message ?: "Login failed"),
        )
      }
      runOnUiThread { evaluateJs(script) }
    }
  }

  private fun evaluateJs(script: String) {
    webViewRef?.evaluateJavascript(script, null)
  }
}
