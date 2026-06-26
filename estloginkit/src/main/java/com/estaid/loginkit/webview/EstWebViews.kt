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
package com.estaid.loginkit.webview

import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.internal.dto.SnsLoginBridge
import com.estaid.loginkit.internal.webview.EstOneWebViewScreen
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.LoginPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 로그인 WebView 컴포저블. (iOS `LoginWebView` 대칭)
 *
 * 자체 Compose 네비게이션에 임베드할 때 사용한다. (Activity 기반은 [EstLoginManager.login])
 * 웹의 SNS 네이티브 로그인 요청을 처리하며, callbackUrl 의 `code` 가 회수되면 [onLoginCompleted]
 * 로 ssoToken 을 전달한다. (토큰 교환은 호스트 책임)
 *
 * @param url 기본값은 [EstLoginManager.loginUrl] — baseUrl+clientId 설정 시 자동 구성.
 */
@Composable
fun EstLoginWebView(
  url: String = EstLoginManager.loginUrl(),
  callbackUrl: String? = EstLoginManager.getConfig()?.callbackUrl,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onPasswordChanged: () -> Unit = {},
  onAccountDeleted: () -> Unit = {},
  onBackPressed: () -> Unit = {},
  onLoginCompleted: (ssoToken: String?) -> Unit,
) {
  val activity = LocalContext.current as? ComponentActivity
  var webViewRef: WebView? = null

  EstOneWebViewScreen(
    url = url,
    callbackUrl = callbackUrl,
    extraUserAgent = extraUserAgent,
    inspectable = inspectable,
    debugMode = EstLoginManager.getConfig()?.debugMode ?: false,
    onWebViewCreated = { wv ->
      webViewRef = wv
      EstLoginManager.getConfig()?.onWebViewCreated?.invoke(wv)
    },
    onSnsLoginRequested = { message ->
      if (activity != null) {
        handleSnsLogin(activity, message) { script -> webViewRef?.evaluateJavascript(script, null) }
      }
    },
    onLoginCompleted = { ssoToken -> onLoginCompleted(ssoToken.ifBlank { null }) },
    onPasswordChanged = onPasswordChanged,
    onAccountDeleted = onAccountDeleted,
    onBackPressed = onBackPressed,
  )
}

/**
 * 마이페이지 WebView 컴포저블. (iOS `MyPageWebView` 대칭)
 *
 * 로그인 세션 쿠키(프로세스 전역 [android.webkit.CookieManager])를 공유하므로 별도 인증 없이 접근된다.
 */
@Composable
fun EstMyPageWebView(
  url: String = EstLoginManager.mypageUrl,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onPasswordChanged: () -> Unit = {},
  onAccountDeleted: () -> Unit = {},
  onBackPressed: () -> Unit = {},
) {
  val activity = LocalContext.current as? ComponentActivity
  var webViewRef: WebView? = null

  EstOneWebViewScreen(
    url = url,
    callbackUrl = null,
    extraUserAgent = extraUserAgent,
    inspectable = inspectable,
    debugMode = EstLoginManager.getConfig()?.debugMode ?: false,
    onWebViewCreated = { wv ->
      webViewRef = wv
      EstLoginManager.getConfig()?.onWebViewCreated?.invoke(wv)
    },
    onSnsLoginRequested = { message ->
      if (activity != null) {
        handleSnsLogin(activity, message) { script -> webViewRef?.evaluateJavascript(script, null) }
      }
    },
    onLoginCompleted = { /* 마이페이지는 로그인 완료 콜백 없음 */ },
    onPasswordChanged = onPasswordChanged,
    onAccountDeleted = onAccountDeleted,
    onBackPressed = onBackPressed,
  )
}

private fun handleSnsLogin(
  activity: ComponentActivity,
  message: String,
  evaluateJs: (String) -> Unit,
) {
  val parsed = SnsLoginBridge.parseRequest(message).getOrNull() ?: return
  val platform = LoginPlatform.from(parsed.provider)
  if (platform == null) {
    val payload = SnsLoginBridge.encodeError(
      provider = parsed.provider,
      code = "unsupported_provider",
      message = "${parsed.provider} native login is not supported. Use WebView OAuth path.",
    )
    activity.runOnUiThread { evaluateJs(SnsLoginBridge.errorScript(payload)) }
    return
  }

  CoroutineScope(Dispatchers.Main).launch {
    val script = try {
      val result = EstLoginManager.socialLogin(activity, platform)
      SnsLoginBridge.successScript(SnsLoginBridge.encodeSuccess(platform, result))
    } catch (e: AuthError) {
      val code = if (e is AuthError.Cancelled) "cancelled" else "sdk_error"
      SnsLoginBridge.errorScript(
        SnsLoginBridge.encodeError(platform.rawValue, code, e.message ?: "Login failed"),
      )
    }
    evaluateJs(script)
  }
}
