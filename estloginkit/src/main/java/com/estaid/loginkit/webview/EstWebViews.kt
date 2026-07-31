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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.internal.dto.SnsLoginBridge
import com.estaid.loginkit.internal.webview.WebViewScreen
import com.estaid.loginkit.internal.webview.VerificationResultDelivery
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.LoginPlatform
import com.estaid.loginkit.model.VerificationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 로그인 WebView 컴포저블.
 *
 * 자체 Compose 네비게이션에 임베드할 때 사용한다. (Activity 기반은 [EstLoginManager.login])
 * 웹의 SNS 네이티브 로그인 요청을 처리하며, callbackUrl 의 `code` 가 회수되면 [onLoginCompleted]
 * 로 ssoToken 을 전달한다. (토큰 교환은 호스트 책임)
 *
 * @param url 기본값은 [EstLoginManager.bootstrapLoginUrl] — 항상 `/auth/sso-login` 부트스트랩으로
 *   진입한다. 부트스트랩을 우회하려면 [EstLoginManager.loginUrl] 을 직접 넘겨라.
 */
@Composable
fun EstLoginWebView(
  url: String = EstLoginManager.bootstrapLoginUrl(),
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

  WebViewScreen(
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
 * 로그인 WebView 컴포저블 — 항상 SSO 부트스트랩(`/auth/sso-login`)으로 진입.
 *
 * - [accessToken] 이 유효하면 fresh ssoToken 을 발급해 세션을 수립한 뒤 로그인 페이지로 진입한다.
 * - [accessToken] 이 null 이면 `code` 없이 부트스트랩으로 열고, 웹이 세션 없음으로 보고 로그인 페이지로 라우팅한다.
 *   (즉 "웹뷰 = 무조건 부트스트랩" — 앱은 토큰 유무로 분기할 필요가 없다)
 *
 * 발급 중에는 로딩이 표시되고, 실패(만료 토큰 등)하면 [onError] 가 호출된다.
 *
 * // url 오버로드와 JVM 시그니처가 겹치지 않도록 accessToken 진입점은 별도 함수명으로 분리한다.
 */
@Composable
fun EstLoginWebViewWithAccessToken(
  accessToken: String?,
  redirectUrl: String? = null,
  state: String? = null,
  callbackUrl: String? = EstLoginManager.getConfig()?.callbackUrl,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onPasswordChanged: () -> Unit = {},
  onAccountDeleted: () -> Unit = {},
  onBackPressed: () -> Unit = {},
  onError: (Throwable) -> Unit = {},
  onLoginCompleted: (ssoToken: String?) -> Unit,
) {
  var resolvedUrl by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(accessToken) {
    try {
      resolvedUrl = EstLoginManager.authorizedLoginUrl(accessToken, redirectUrl, state)
    } catch (e: Exception) {
      onError(e)
    }
  }

  val url = resolvedUrl
  if (url == null) {
    BootstrapLoading()
    return
  }

  EstLoginWebView(
    url = url,
    callbackUrl = callbackUrl,
    extraUserAgent = extraUserAgent,
    inspectable = inspectable,
    onPasswordChanged = onPasswordChanged,
    onAccountDeleted = onAccountDeleted,
    onBackPressed = onBackPressed,
    onLoginCompleted = onLoginCompleted,
  )
}

/**
 * 마이페이지 WebView 컴포저블 — SSO 부트스트랩 진입. (권장)
 *
 * 유효한 accessToken만 넘기면 SDK가 ssoToken 발급 → SSO 부트스트랩 → 마이페이지 진입까지
 * 처리한다. 발급 중에는 로딩 인디케이터가 표시되고, 실패(만료 토큰 등)하면 [onError]가
 * 호출된다(화면 닫기는 호스트 몫). 웹뷰 쿠키가 없거나 만료된 상태에서도 로그인 화면 없이 열린다.
 *
 * @param accessToken 앱이 보유한 유효한 accessToken. 만료 판단·갱신은 앱 책임.
 * @param onError ssoToken 발급 실패 시 호출. 만료 토큰이면 [AuthError.Server] (statusCode 401).
 */
@Composable
fun EstMyPageWebView(
  accessToken: String,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onPasswordChanged: () -> Unit = {},
  onAccountDeleted: () -> Unit = {},
  onBackPressed: () -> Unit = {},
  onError: (Throwable) -> Unit = {},
) {
  var resolvedUrl by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(accessToken) {
    try {
      resolvedUrl = EstLoginManager.authorizedMypageUrl(accessToken)
    } catch (e: Exception) {
      onError(e)
    }
  }

  val url = resolvedUrl
  if (url == null) {
    BootstrapLoading()
    return
  }

  EstMyPageWebView(
    url = url,
    extraUserAgent = extraUserAgent,
    inspectable = inspectable,
    onPasswordChanged = onPasswordChanged,
    onAccountDeleted = onAccountDeleted,
    onBackPressed = onBackPressed,
  )
}

/**
 * 마이페이지 WebView 컴포저블 — URL 직접 진입.
 *
 * 로그인 세션 쿠키(프로세스 전역 [android.webkit.CookieManager])가 살아있을 때만 접근된다.
 * 쿠키가 없으면 로그인 화면이 뜨므로 일반적으로는 accessToken 오버로드를 사용하라.
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

  WebViewScreen(
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

/**
 * 본인인증 WebView 컴포저블 — SSO 부트스트랩 진입.
 *
 * 유효한 accessToken만 넘기면 SDK가 ssoToken 발급 → SSO 부트스트랩 → 본인인증 진입까지
 * 처리한다. 발급 중에는 로딩 인디케이터가 표시되고, 실패(만료 토큰 등)하면 [onResult]로
 * `Result.failure`가 전달된다. 웹뷰 쿠키가 없거나 만료된 상태에서도 동작한다.
 *
 * 완료 통지는 [callbackUrl] 리다이렉트 한 경로로만 들어온다. 웹이 리다이렉트를 재시도해도
 * [onResult] 는 1회만 호출된다.
 *
 * @param accessToken 앱이 보유한 유효한 accessToken. 만료 판단·갱신은 앱 책임.
 * @param callbackUrl 브릿지 미등록 시 리다이렉트될 앱 콜백 URL. 브릿지가 우선이므로 생략해도 동작한다.
 * @param onResult 발급 실패 시 [AuthError.Server] (statusCode 401) 등, 사용자 취소 시
 *   [AuthError.Cancelled], 승격/병합 실패 시 [AuthError.VerificationFailed].
 */
@Composable
fun EstVerificationWebView(
  accessToken: String,
  callbackUrl: String? = null,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onBackPressed: () -> Unit = {},
  onResult: (Result<VerificationResult>) -> Unit,
) {
  var resolvedUrl by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(accessToken) {
    try {
      resolvedUrl = EstLoginManager.authorizedVerificationUrl(accessToken, callbackUrl)
    } catch (e: Exception) {
      onResult(Result.failure(e))
    }
  }

  val url = resolvedUrl
  if (url == null) {
    BootstrapLoading()
    return
  }

  val activity = LocalContext.current as? ComponentActivity
  var webViewRef: WebView? = null
  val delivery = remember(onResult) { VerificationResultDelivery(onResult) }

  WebViewScreen(
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
    onLoginCompleted = { /* 본인인증은 로그인 완료 콜백 미사용 — 결과는 verificationDelivery 로 전달 */ },
    onPasswordChanged = {},
    onAccountDeleted = {},
    onBackPressed = onBackPressed,
    verificationDelivery = delivery,
  )
}

/** SSO 부트스트랩 URL 발급 동안 표시하는 로딩 화면. */
@Composable
private fun BootstrapLoading() {
  Box(modifier = Modifier.fillMaxSize()) {
    CircularProgressIndicator(
      modifier = Modifier
        .align(Alignment.Center)
        .size(48.dp),
    )
  }
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
