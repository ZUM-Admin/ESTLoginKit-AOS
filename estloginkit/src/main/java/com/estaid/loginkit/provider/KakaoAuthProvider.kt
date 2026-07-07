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
import com.kakao.sdk.auth.model.OAuthToken
import com.kakao.sdk.common.model.AuthErrorCause
import com.kakao.sdk.common.model.ClientError
import com.kakao.sdk.common.model.ClientErrorCause
import com.kakao.sdk.user.UserApiClient
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import com.kakao.sdk.common.model.AuthError as KakaoAuthError

/**
 * 카카오 네이티브 로그인. (iOS `KakaoAuthProvider` 대칭)
 *
 * 카카오톡 앱이 가능하면 앱 로그인, 아니면 카카오계정 웹 로그인으로 폴백한다.
 */
internal class KakaoAuthProvider : AuthProvider {

  override suspend fun login(activity: ComponentActivity): AuthResult =
    suspendCancellableCoroutine { continuation ->
      val callback: (OAuthToken?, Throwable?) -> Unit = callback@{ token, error ->
        if (!continuation.isActive) return@callback
        when {
          error != null -> continuation.resumeWithException(mapError(error))
          token?.accessToken.isNullOrBlank() ->
            continuation.resumeWithException(
              AuthError.Unknown(IllegalStateException("Kakao authorize token is empty.")),
            )
          else -> continuation.resume(
            AuthResult(
              authorizeToken = token!!.accessToken,
              refreshToken = token.refreshToken.orEmpty(),
            ),
          )
        }
      }

      val talkAvailable = UserApiClient.instance.isKakaoTalkLoginAvailable(activity)
      EstLog.debug("KakaoTalk login available=$talkAvailable")

      if (talkAvailable) {
        UserApiClient.instance.loginWithKakaoTalk(activity) { token, error ->
          when {
            error == null -> callback(token, null)
            error is ClientError && error.reason == ClientErrorCause.Cancelled -> callback(null, error)
            else -> {
              EstLog.error("KakaoTalk login failed, falling back to account web login.", error)
              UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
            }
          }
        }
      } else {
        UserApiClient.instance.loginWithKakaoAccount(activity, callback = callback)
      }
    }

  private fun mapError(error: Throwable): AuthError = when {
    error is ClientError && error.reason == ClientErrorCause.Cancelled -> AuthError.Cancelled
    error is KakaoAuthError && error.reason == AuthErrorCause.AccessDenied -> AuthError.Cancelled
    else -> AuthError.Unknown(error)
  }
}
