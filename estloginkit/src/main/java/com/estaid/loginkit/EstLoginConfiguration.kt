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
package com.estaid.loginkit

import android.webkit.WebView
import com.estaid.loginkit.model.KakaoConfiguration
import com.estaid.loginkit.model.NaverConfiguration

/**
 * ESTLoginKit 설정. (iOS `ESTLoginConfiguration` 대칭)
 *
 * iOS 와 동일하게 Builder 로 생성한다:
 * ```
 * val config = EstLoginConfiguration.Builder(clientId = "...")
 *   .useBaseUrl("https://test.estoneid.com")
 *   .useKakao(KakaoConfiguration(appKey = "..."))
 *   .useNaver(NaverConfiguration(appName = "...", clientId = "...", clientSecret = "..."))
 *   .build()
 * ```
 *
 * 인증 서버(AUTH_API)는 [baseUrl] 에 대응해 자동 결정된다. (README_v2 §설정)
 */
class EstLoginConfiguration private constructor(
  val clientId: String,
  val baseUrl: String,
  val kakaoConfig: KakaoConfiguration?,
  val naverConfig: NaverConfiguration?,
  // --- Android WebView 옵션 (iOS 는 View 파라미터로 받지만 Android 는 편의상 config 로도 노출) ---
  val callbackUrl: String?,
  val extraUserAgent: String?,
  val webViewInspectable: Boolean,
  val debugMode: Boolean,
  /** SDK WebView 생성 직후 1회 호출 — 호스트가 자체 SDK(예: Hackle Bridge) wiring 용. */
  val onWebViewCreated: ((WebView) -> Unit)?,
  /** 마이페이지에서 비밀번호 변경 통지 — 호스트가 silent 재발급 처리. */
  val onPasswordChanged: (() -> Unit)?,
  /** 마이페이지에서 회원 탈퇴 통지 — 호스트가 로그아웃 처리. */
  val onAccountDeleted: (() -> Unit)?,
) {
  companion object {
    const val DEFAULT_BASE_URL = "https://estoneid.com"
  }

  class Builder(private val clientId: String) {
    private var baseUrl: String = DEFAULT_BASE_URL
    private var kakaoConfig: KakaoConfiguration? = null
    private var naverConfig: NaverConfiguration? = null
    private var callbackUrl: String? = null
    private var extraUserAgent: String? = null
    private var webViewInspectable: Boolean = false
    private var debugMode: Boolean = false
    private var onWebViewCreated: ((WebView) -> Unit)? = null
    private var onPasswordChanged: (() -> Unit)? = null
    private var onAccountDeleted: (() -> Unit)? = null

    fun useBaseUrl(baseUrl: String) = apply { this.baseUrl = baseUrl }

    fun useKakao(config: KakaoConfiguration?) = apply { this.kakaoConfig = config }

    fun useNaver(config: NaverConfiguration?) = apply { this.naverConfig = config }

    fun useCallbackUrl(callbackUrl: String?) = apply { this.callbackUrl = callbackUrl }

    fun useExtraUserAgent(extraUserAgent: String?) = apply { this.extraUserAgent = extraUserAgent }

    fun webViewInspectable(enabled: Boolean) = apply { this.webViewInspectable = enabled }

    fun debugMode(enabled: Boolean) = apply { this.debugMode = enabled }

    fun onWebViewCreated(block: ((WebView) -> Unit)?) = apply { this.onWebViewCreated = block }

    fun onPasswordChanged(block: (() -> Unit)?) = apply { this.onPasswordChanged = block }

    fun onAccountDeleted(block: (() -> Unit)?) = apply { this.onAccountDeleted = block }

    fun build(): EstLoginConfiguration = EstLoginConfiguration(
      clientId = clientId,
      baseUrl = baseUrl,
      kakaoConfig = kakaoConfig,
      naverConfig = naverConfig,
      callbackUrl = callbackUrl ?: "$baseUrl/auth/app-callback",
      extraUserAgent = extraUserAgent,
      webViewInspectable = webViewInspectable,
      debugMode = debugMode,
      onWebViewCreated = onWebViewCreated,
      onPasswordChanged = onPasswordChanged,
      onAccountDeleted = onAccountDeleted,
    )
  }
}
