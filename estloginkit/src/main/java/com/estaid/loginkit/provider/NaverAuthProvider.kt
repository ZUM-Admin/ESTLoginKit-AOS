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
package com.estaid.loginkit.provider

import androidx.activity.ComponentActivity
import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.AuthResult
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.util.NidOAuthCallback
import com.navercorp.nid.profile.domain.vo.NidProfile
import com.navercorp.nid.profile.util.NidProfileCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 네이버 네이티브 로그인.
 */
internal class NaverAuthProvider : AuthProvider {

  override suspend fun login(activity: ComponentActivity): AuthResult =
    suspendCancellableCoroutine { continuation ->
      NidOAuth.requestLogin(
        context = activity,
        callback = object : NidOAuthCallback {
          override fun onSuccess() {
            if (!continuation.isActive) return
            val accessToken = NidOAuth.getAccessToken()
            if (accessToken.isNullOrBlank()) {
              continuation.resumeWithException(
                AuthError.Unknown(IllegalStateException("Naver authorize token is empty.")),
              )
              return
            }

            fun resumeSuccess(email: String?, ci: String?) {
              if (!continuation.isActive) return
              continuation.resume(
                AuthResult(
                  authorizeToken = accessToken,
                  refreshToken = NidOAuth.getRefreshToken().orEmpty(),
                  ci = ci?.takeIf { it.isNotBlank() }.orEmpty(),
                  email = email?.takeIf { it.isNotBlank() }.orEmpty(),
                ),
              )
            }

            // email/ci 는 옵셔널 — 프로필 조회 실패/미동의/미제휴 시에도 로그인은 성공으로 처리한다. 웹이
            // 이메일 인풋을 prefill 하려면 email 이 필요하다(브릿지 payload 로 전달). ci 는 별도 제휴 없이는
            // 응답에 없을 수 있다(그 경우 빈 값). (구 est-auth-sdk 대칭)
            NidOAuth.getUserProfile(
              object : NidProfileCallback<NidProfile> {
                override fun onSuccess(result: NidProfile) {
                  resumeSuccess(result.profile?.email, result.profile?.ci)
                }

                override fun onFailure(errorCode: String, errorDesc: String) {
                  EstLog.debug("Naver profile fetch failed (email/ci skipped): $errorCode $errorDesc")
                  resumeSuccess(null, null)
                }
              },
            )
          }

          override fun onFailure(errorCode: String, errorDesc: String) {
            if (!continuation.isActive) return
            EstLog.debug("Naver onFailure: code=$errorCode desc=$errorDesc")
            continuation.resumeWithException(mapError(errorCode, errorDesc))
          }
        },
      )
    }

  private fun mapError(errorCode: String, errorDesc: String): AuthError = when {
    errorCode.equals("user_cancel", ignoreCase = true) ||
      errorCode.equals("CLIENT_USER_CANCEL", ignoreCase = true) -> AuthError.Cancelled
    else -> AuthError.Unknown(IllegalStateException("Naver login failed: $errorCode $errorDesc"))
  }
}
