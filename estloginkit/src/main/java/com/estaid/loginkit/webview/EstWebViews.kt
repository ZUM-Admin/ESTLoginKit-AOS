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
import com.estaid.loginkit.internal.webview.EstOneWebViewScreen
import com.estaid.loginkit.internal.webview.VerificationResultDelivery
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.LoginPlatform
import com.estaid.loginkit.model.VerificationResult
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
 * 마이페이지 WebView 컴포저블 — SSO 부트스트랩 진입. (iOS `MyPageWebView(accessToken:)` 대칭. 권장)
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
 * 마이페이지 WebView 컴포저블 — URL 직접 진입. (iOS `MyPageWebView(url:)` 대칭)
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

/**
 * 본인인증 WebView 컴포저블 — SSO 부트스트랩 진입. (iOS `IdentityVerificationView(accessToken:)` 대칭. 권장)
 *
 * 유효한 accessToken만 넘기면 SDK가 ssoToken 발급 → SSO 부트스트랩 → 본인인증 진입까지
 * 처리한다. 발급 중에는 로딩 인디케이터가 표시되고, 실패(만료 토큰 등)하면 [onResult]로
 * `Result.failure`가 전달된다. 웹뷰 쿠키가 없거나 만료된 상태에서도 동작한다.
 *
 * @param accessToken 앱이 보유한 유효한 accessToken. 만료 판단·갱신은 앱 책임.
 * @param callbackUrl 브릿지 미등록 시 리다이렉트될 앱 콜백 URL. 브릿지가 우선이므로 생략해도 동작한다.
 * @param onResult 발급 실패 시 [AuthError.Server] (statusCode 401) 등, 사용자 취소 시
 *   [AuthError.Cancelled], 승격/병합 실패 시 [AuthError.VerificationFailed].
 */
// url 오버로드와 JVM 시그니처가 동일해 같은 이름 오버로드로 둘 수 없다(@Composable 은 @JvmName 을
// 무시하므로 JVM 레벨 분리가 불가). accessToken 진입점은 별도 함수명으로 분리한다.
@Composable
fun EstIdentityVerificationWebViewWithAccessToken(
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

  EstIdentityVerificationWebView(
    url = url,
    callbackUrl = callbackUrl,
    extraUserAgent = extraUserAgent,
    inspectable = inspectable,
    onBackPressed = onBackPressed,
    onResult = onResult,
  )
}

/**
 * 본인인증 WebView 컴포저블 — URL 직접 진입. (iOS `IdentityVerificationView(url:)` 대칭)
 *
 * 세션 쿠키가 살아있을 때만 임시 회원 세션이 전달된다. 쿠키가 없으면 로그인 화면이 뜨므로
 * 일반적으로는 accessToken 오버로드를 사용하라.
 *
 * 화면 콘텐츠와 완료 통지 수신은 SDK 가 담당하고, **언제 띄울지(정책)와 화면을 감싸 present/dismiss
 * 하는 것은 호스트**가 결정한다. 인증 여부는 [EstLoginManager.verificationStatus] 로 조회한다.
 *
 * 로그인 세션 쿠키(프로세스 전역 [android.webkit.CookieManager])를 공유하므로 임시 회원 세션이
 * 그대로 전달되며, 인증 회원 승격과 CI 충돌 해소는 웹뷰가 자체 처리한다.
 *
 * 완료 통지는 ① 브릿지(`onVerificationComplete`) → ② (브릿지 미등록 시) [callbackUrl] 리다이렉트
 * 순으로 들어오며 SDK 가 둘 다 처리한다. 두 경로가 모두 도착해도 [onResult] 는 1회만 호출된다.
 *
 * @param url 기본값은 [EstLoginManager.verificationUrl] — [callbackUrl] 을 조합해 구성한다.
 * @param callbackUrl 브릿지 미등록 시 리다이렉트될 앱 콜백 URL. 브릿지가 우선이므로 생략해도 동작한다.
 * @param onResult 사용자 취소 시 [AuthError.Cancelled], 승격/병합 실패 시 [AuthError.VerificationFailed].
 */
@Composable
fun EstIdentityVerificationWebView(
  url: String? = null,
  callbackUrl: String? = null,
  extraUserAgent: String? = EstLoginManager.getConfig()?.extraUserAgent,
  inspectable: Boolean = EstLoginManager.getConfig()?.webViewInspectable ?: false,
  onBackPressed: () -> Unit = {},
  onResult: (Result<VerificationResult>) -> Unit,
) {
  val activity = LocalContext.current as? ComponentActivity
  var webViewRef: WebView? = null
  val delivery = remember(onResult) { VerificationResultDelivery(onResult) }

  EstOneWebViewScreen(
    url = url ?: EstLoginManager.verificationUrl(callbackUrl),
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
