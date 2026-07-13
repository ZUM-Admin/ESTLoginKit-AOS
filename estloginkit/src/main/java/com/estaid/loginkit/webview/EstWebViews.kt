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

/**
 * 본인인증 WebView 컴포저블. (iOS `IdentityVerificationView` / `IdentityVerificationViewController` 대칭)
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
