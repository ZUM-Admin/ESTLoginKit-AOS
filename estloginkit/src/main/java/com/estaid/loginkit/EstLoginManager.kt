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

import android.app.Activity
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.internal.SocialLoginInitializer
import com.estaid.loginkit.internal.SsoBootstrap
import com.estaid.loginkit.internal.network.AuthNetworkClient
import com.estaid.loginkit.internal.webview.WebViewActivity
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.AuthResult
import com.estaid.loginkit.model.LoginPlatform
import com.estaid.loginkit.model.VerificationStatus
import com.estaid.loginkit.provider.AuthProvider
import com.estaid.loginkit.provider.KakaoAuthProvider
import com.estaid.loginkit.provider.NaverAuthProvider
import com.kakao.sdk.user.UserApiClient
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.util.NidOAuthCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.net.URI
import java.net.URLEncoder
import kotlin.coroutines.resume

/**
 * ESTLoginKit 진입점. (iOS `ESTLoginManager.shared` 대칭)
 *
 * SDK 는 stateless 이다 — ssoToken→accessToken 교환, 토큰 저장/갱신/만료는 호스트 책임.
 */
object EstLoginManager {
  private var appContext: Context? = null
  private var config: EstLoginConfiguration? = null
  private var initializer: SocialLoginInitializer? = null
  private var networkClient: AuthNetworkClient? = null
  private val providers = mutableMapOf<LoginPlatform, AuthProvider>()

  /** 앱 진입점에서 1회 호출. */
  fun initialize(context: Context, config: EstLoginConfiguration) {
    val app = context.applicationContext
    appContext = app
    this.config = config
    EstLog.debugEnabled = config.debugMode

    initializer = SocialLoginInitializer(app, config).also { it.ensureInitialized() }
    networkClient = AuthNetworkClient(
      authApiBaseUrl = config.apiBaseUrl,
      debugMode = config.debugMode,
    )

    providers.clear()
    if (config.kakaoConfig != null) providers[LoginPlatform.KAKAO] = KakaoAuthProvider()
    if (config.naverConfig != null) providers[LoginPlatform.NAVER] = NaverAuthProvider()

    EstLog.info("initialized: env=${config.environment}, apiBaseUrl=${config.apiBaseUrl}")
  }

  fun getConfig(): EstLoginConfiguration? = config

  // region 네이티브 로그인 (카카오/네이버)

  /** 네이티브 소셜 로그인. (iOS `login(with:)`) */
  suspend fun login(activity: ComponentActivity, platform: LoginPlatform): AuthResult =
    socialLogin(activity, platform)

  /** 웹 JS 브릿지에서도 호출하는 네이티브 로그인 진입점. */
  suspend fun socialLogin(activity: ComponentActivity, platform: LoginPlatform): AuthResult {
    val provider = providers[platform]
      ?: throw AuthError.UnsupportedPlatform
    return provider.login(activity)
  }

  // endregion

  // region 웹뷰 로그인 (ssoToken 회수)

  /**
   * WebView 로그인 화면을 띄우고 ssoToken 을 회수한다. (Android 편의 — iOS 는 `LoginWebView` 사용)
   * 토큰 교환은 호스트 책임이다.
   *
   * [url] 을 직접 주지 않으면 **항상 `/auth/sso-login` 부트스트랩**으로 진입한다(iOS 기본 방식).
   * 부트스트랩이 웹 세션을 먼저 검증·정리하므로 쿠키 잔존 상태 차이 없이 동일하게 로그인 페이지로 진입한다.
   *
   * @return ssoToken (취소 시 [AuthError.Cancelled])
   */
  suspend fun startWebLogin(
    activity: ComponentActivity,
    url: String? = null,
    redirectUrl: String? = null,
    state: String? = null,
    extraUserAgent: String? = null,
  ): Result<String?> {
    val cfg = config ?: return Result.failure(AuthError.NotInitialized)
    // 우선순위: 호출 시 직접 전달 URL → 부트스트랩(`/auth/sso-login`)으로 감싼 로그인 URL(기본)
    val resolvedUrl = url?.takeIf { it.isNotBlank() }
      ?: bootstrapLoginUrl(redirectUrl = redirectUrl, state = state)
    return suspendCancellableCoroutine { continuation ->
      val launcher = activity.activityResultRegistry.register(
        "estloginkit-weblogin",
        ActivityResultContracts.StartActivityForResult(),
      ) { result ->
        if (!continuation.isActive) return@register
        if (result.resultCode == Activity.RESULT_OK) {
          val ssoToken = result.data?.getStringExtra(WebViewActivity.RESULT_SSO_TOKEN)
          continuation.resume(Result.success(ssoToken))
        } else {
          continuation.resume(Result.failure(AuthError.Cancelled))
        }
      }
      val intent = WebViewActivity.createIntent(
        context = activity,
        loginUrl = resolvedUrl,
        callbackUrl = cfg.callbackUrl,
        extraUserAgent = extraUserAgent ?: cfg.extraUserAgent,
        inspectable = cfg.webViewInspectable,
        debugMode = cfg.debugMode,
      )
      launcher.launch(intent)
    }
  }

  // endregion

  // region URL 빌더

  /** 로그인 URL 빌더. (iOS `loginURL(redirectURL:state:silent:)`) */
  fun loginUrl(redirectUrl: String? = null, state: String? = null, silent: Boolean = false): String {
    val cfg = requireConfig()
    val base = cfg.baseUrl
    val actualRedirect = redirectUrl ?: "$base/auth/app-callback"
    val params = buildList {
      add("type" to "callback")
      add("client_id" to cfg.clientId)
      add("redirect_url" to actualRedirect)
      if (!state.isNullOrBlank()) add("state" to state)
      if (silent) add("silent" to "true")
    }.joinToString("&") { (k, v) -> "$k=${encode(v)}" }
    return "$base/user/login?$params"
  }

  /**
   * 로그인 부트스트랩 URL 빌더 — 내부 `/user/login` 을 항상 `/auth/sso-login` 으로 감싼다.
   * (iOS 기본 진입 방식과 동일)
   *
   * `code` 없이 열리며, 웹이 기존 est 세션을 먼저 검증·정리한 뒤 로그인 페이지로 라우팅한다.
   * 따라서 쿠키 잔존 상태에 상관없이 항상 동일하게 진입한다. accessToken 으로 세션까지 수립하려면
   * [authorizedLoginUrl] 을 사용하라.
   */
  fun bootstrapLoginUrl(redirectUrl: String? = null, state: String? = null): String {
    val cfg = requireConfig()
    // loginUrl(/user/login?...) 을 부트스트랩 redirect_url 로 감싼다. 이때 안쪽 쿼리 값
    // (redirect_url·state 는 그 자체가 URL)을 **이미 1회 인코딩된 상태(rawQuery) 그대로** 넘겨
    // SsoBootstrap 이 한 번 더 인코딩하게 한다 → 이중 인코딩. 디코드된 값(단일 인코딩)을 넘기면
    // SDK 의 중첩 state 추출([resolveInitialState])이 깨져 top-level state 가 없는 것으로 판정되고,
    // union-user-api 콜백에서 조기 완료(callbackUrl 매칭)돼 EZSID 세션이 커밋되지 않는다.
    // (iOS LoginFullScreenView.bootstrapLoginURL 대칭)
    val inner = URI(loginUrl(redirectUrl, state))
    val redirectValue = inner.rawPath + inner.rawQuery?.let { "?$it" }.orEmpty()
    return SsoBootstrap.ssoLoginUrl(
      baseUrl = cfg.baseUrl,
      redirectUrl = redirectValue,
      ssoToken = null,
    )
  }

  /** 마이페이지 URL. (iOS `mypageURL`) */
  val mypageUrl: String
    get() = "${requireConfig().baseUrl}/mypage/setting"

  /**
   * 본인인증 화면 URL. (iOS `verificationURL(callbackURL:)`)
   *
   * 웹뷰가 임시 회원의 로그인 세션 쿠키를 갖고 있어야 하며, 인증 회원 승격과 CI 충돌 해소는
   * 웹뷰가 자체 처리한다. 완료 통지는 [callbackUrl] 리다이렉트로만 전달된다.
   *
   * @param callbackUrl 완료 시 리다이렉트될 앱 콜백 URL.
   *   **생략하면 결과를 받을 경로가 없다** — 화면이 인증 흐름의 종착점이면 반드시 지정하라.
   */
  fun verificationUrl(callbackUrl: String? = null): String {
    val path = "${requireConfig().baseUrl}/auth/verification"
    return if (callbackUrl.isNullOrBlank()) path else "$path?callbackURL=${encode(callbackUrl)}"
  }

  // endregion

  // region SSO 부트스트랩 (웹뷰 세션 수립)

  /**
   * 일회성 SSO 토큰을 발급받는다. (iOS `issueSSOToken(accessToken:)` 대칭)
   *
   * `GET {apiBaseUrl}/auth/sso/sso-token` (`Authorization: Bearer`)
   *
   * SDK 는 stateless — 유효한 accessToken 을 파라미터로 받는다고 가정한다. 만료 판단·갱신은
   * 앱 책임이며, 만료된 토큰이면 [AuthError.Server] (statusCode 401)가 던져진다. 갱신 후
   * 재호출은 앱 몫이다.
   *
   * 토큰은 **유효 60초, 1회용** — 웹뷰를 여는 시점마다 새로 발급하고, 저장·로그 출력하지 않는다.
   *
   * @return AES256 암호화된 일회성 SSO 토큰 (유효 60초, 파싱 금지)
   */
  suspend fun issueSsoToken(accessToken: String): String {
    val client = networkClient ?: throw AuthError.NotInitialized
    return try {
      withContext(Dispatchers.IO) {
        client.authApiService.issueSsoToken("Bearer $accessToken").result.ssoToken
      }
    } catch (e: HttpException) {
      throw AuthError.Server(statusCode = e.code())
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unknown(e)
    }
  }

  /**
   * 마이페이지로 이동하는 SSO 부트스트랩 URL. (iOS `authorizedMypageRequest(accessToken:)` 대칭)
   * 웹이 code 검증 후 자체 세션을 수립하고 이동시키므로 웹뷰 쿠키가 없어도 열린다.
   *
   * ssoToken 은 유효 60초이므로 웹뷰를 여는 시점마다 새로 호출해야 한다.
   * (미리 만들어 두면 만료돼 사용자가 불필요하게 로그인 화면을 보게 된다)
   */
  suspend fun authorizedMypageUrl(accessToken: String): String {
    val ssoToken = issueSsoToken(accessToken)
    return SsoBootstrap.ssoLoginUrl(
      baseUrl = requireConfig().baseUrl,
      redirectUrl = SsoBootstrap.redirectUrlValue(mypageUrl),
      ssoToken = ssoToken,
    )
  }

  /**
   * 로그인 화면으로 이동하는 SSO 부트스트랩 URL. (마이페이지/본인인증과 동일 방식)
   *
   * 로그인 웹뷰는 항상 `/auth/sso-login` 부트스트랩으로 연다.
   * - [accessToken] 이 유효하면 fresh ssoToken 을 발급해 `code` 로 실어 보내 세션을 수립한다.
   * - [accessToken] 이 null/빈 값이면 `code` 없이 열고, 웹이 세션 없음으로 보고 로그인 페이지로 라우팅한다.
   *
   * ssoToken 은 유효 60초이므로 웹뷰를 여는 시점마다 새로 호출해야 한다.
   */
  suspend fun authorizedLoginUrl(
    accessToken: String?,
    redirectUrl: String? = null,
    state: String? = null,
  ): String {
    val ssoToken = accessToken?.takeIf { it.isNotBlank() }?.let { issueSsoToken(it) }
    return SsoBootstrap.ssoLoginUrl(
      baseUrl = requireConfig().baseUrl,
      redirectUrl = SsoBootstrap.redirectUrlValue(loginUrl(redirectUrl, state)),
      ssoToken = ssoToken,
    )
  }

  /** 본인인증으로 이동하는 SSO 부트스트랩 URL. (iOS `authorizedVerificationRequest(accessToken:callbackURL:)` 대칭) */
  suspend fun authorizedVerificationUrl(accessToken: String, callbackUrl: String? = null): String {
    val ssoToken = issueSsoToken(accessToken)
    return SsoBootstrap.ssoLoginUrl(
      baseUrl = requireConfig().baseUrl,
      redirectUrl = SsoBootstrap.redirectUrlValue(verificationUrl(callbackUrl)),
      ssoToken = ssoToken,
    )
  }

  // endregion

  // region 로그아웃

  /**
   * 로그아웃 — best-effort. (iOS `logout()`)
   *
   * 네이버/카카오 네이티브 토큰을 각각 독립적으로 삭제한다.
   *
   * 웹 세션(쿠키/스토리지)은 SDK 가 건드리지 않는다 — 웹 세션은 웹이 소유하며,
   * est 웹뷰는 열 때마다 accessToken 부트스트랩으로 세션을 새로 검증·수립하므로
   * 앱 로그아웃 시 로컬 웹 데이터를 지울 필요가 없다.
   * 호스트가 직접 저장한 accessToken/refreshToken 은 SDK 가 보관하지 않으므로
   * 호스트가 직접 삭제해야 한다.
   */
  suspend fun logout() {
    val cfg = config ?: return
    if (cfg.kakaoConfig != null) runCatching { kakaoLogout() }.onFailure { EstLog.error("Kakao logout failed", it) }
    if (cfg.naverConfig != null) runCatching { naverLogout() }.onFailure { EstLog.error("Naver logout failed", it) }
  }

  private suspend fun kakaoLogout(): Unit = suspendCancellableCoroutine { continuation ->
    UserApiClient.instance.logout { error ->
      if (error != null) EstLog.error("Kakao logout error (ignored)", error)
      if (continuation.isActive) continuation.resume(Unit)
    }
  }

  private suspend fun naverLogout(): Unit = suspendCancellableCoroutine { continuation ->
    NidOAuth.logout(object : NidOAuthCallback {
      override fun onSuccess() {
        if (continuation.isActive) continuation.resume(Unit)
      }

      override fun onFailure(errorCode: String, errorDesc: String) {
        EstLog.error("Naver logout failed: $errorCode $errorDesc")
        if (continuation.isActive) continuation.resume(Unit)
      }
    })
  }

  // endregion

  // region 본인인증 조회

  /**
   * 회원 본인인증 상태 조회. (iOS `verificationStatus(accessToken:)`)
   *
   * SDK 는 토큰을 보관하지 않으므로 호스트가 [accessToken] 을 주입한다.
   * `GET /members/v1/certification/status` 를 `Authorization: Bearer {accessToken}` 으로 호출하며,
   * 응답 `result.status` 가 `CERTIFIED` 이면 [VerificationStatus.isVerified] 가 `true` 이다.
   *
   * @throws AuthError.NotInitialized `initialize(...)` 미호출
   * @throws AuthError.Server 서버가 2xx 아닌 상태로 응답 (`statusCode == 401` 이면 토큰 갱신 후 재시도)
   * @throws AuthError.Unknown 네트워크 오류 등 그 외 (원본 에러 wrapping)
   */
  suspend fun verificationStatus(accessToken: String): VerificationStatus {
    val client = networkClient ?: throw AuthError.NotInitialized
    return try {
      val response = withContext(Dispatchers.IO) {
        client.authApiService.verificationStatus("Bearer $accessToken")
      }
      VerificationStatus(
        isVerified = response.result.status.equals("CERTIFIED", ignoreCase = true),
      )
    } catch (e: HttpException) {
      throw AuthError.Server(statusCode = e.code())
    } catch (e: AuthError) {
      throw e
    } catch (e: Exception) {
      throw AuthError.Unknown(e)
    }
  }

  // endregion

  private fun requireConfig(): EstLoginConfiguration =
    config ?: throw AuthError.NotInitialized

  private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}
