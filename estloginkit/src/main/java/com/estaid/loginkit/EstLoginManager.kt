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
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.internal.SocialLoginInitializer
import com.estaid.loginkit.internal.SsoBootstrap
import com.estaid.loginkit.internal.network.AuthNetworkClient
import com.estaid.loginkit.internal.webview.EstOneWebViewActivity
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
    // 우선순위: 호출 시 직접 전달 URL → baseUrl+clientId 로 빌드한 로그인 URL
    val resolvedUrl = url?.takeIf { it.isNotBlank() }
      ?: loginUrl(redirectUrl = redirectUrl, state = state)
    return suspendCancellableCoroutine { continuation ->
      val launcher = activity.activityResultRegistry.register(
        "estloginkit-weblogin",
        ActivityResultContracts.StartActivityForResult(),
      ) { result ->
        if (!continuation.isActive) return@register
        if (result.resultCode == Activity.RESULT_OK) {
          val ssoToken = result.data?.getStringExtra(EstOneWebViewActivity.RESULT_SSO_TOKEN)
          continuation.resume(Result.success(ssoToken))
        } else {
          continuation.resume(Result.failure(AuthError.Cancelled))
        }
      }
      val intent = EstOneWebViewActivity.createIntent(
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

  /** 마이페이지 URL. (iOS `mypageURL`) */
  val mypageUrl: String
    get() = "${requireConfig().baseUrl}/mypage/setting"

  /**
   * 본인인증 화면 URL. (iOS `verificationURL(callbackURL:)`)
   *
   * 웹뷰가 임시 회원의 로그인 세션 쿠키를 갖고 있어야 하며, 인증 회원 승격과 CI 충돌 해소는
   * 웹뷰가 자체 처리한다. 완료 통지는 브릿지(`onVerificationComplete`) 우선이고, 브릿지가 없을
   * 때만 [callbackUrl] 로 리다이렉트되므로 둘 중 하나만 처리하면 된다.
   *
   * @param callbackUrl 브릿지 미등록 시 리다이렉트될 앱 콜백 URL. (선택)
   */
  fun verificationUrl(callbackUrl: String? = null): String {
    val path = "${requireConfig().baseUrl}/webview/verification"
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
   * 네이버/카카오 네이티브 토큰 삭제와 WebView 세션 데이터(쿠키 + localStorage 등) 삭제를
   * 각각 독립적으로 수행한다. 호스트가 직접 저장한 accessToken/refreshToken 은 SDK 가
   * 보관하지 않으므로 호스트가 직접 삭제해야 한다.
   */
  suspend fun logout() {
    val cfg = config ?: return
    if (cfg.kakaoConfig != null) runCatching { kakaoLogout() }.onFailure { EstLog.error("Kakao logout failed", it) }
    if (cfg.naverConfig != null) runCatching { naverLogout() }.onFailure { EstLog.error("Naver logout failed", it) }
    clearWebSession()
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

  private suspend fun clearWebSession() = withContext(Dispatchers.Main) {
    runCatching {
      CookieManager.getInstance().apply {
        removeAllCookies(null)
        flush()
      }
      WebStorage.getInstance().deleteAllData()
    }.onFailure { EstLog.error("clearWebSession failed", it) }
    Unit
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
