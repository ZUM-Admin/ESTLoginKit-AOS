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

import android.util.Log
import androidx.activity.ComponentActivity
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.AuthResult
import com.navercorp.nid.NidOAuth
import com.navercorp.nid.oauth.util.NidOAuthCallback
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 네이버 네이티브 로그인. (iOS `NaverAuthProvider` 대칭)
 */
internal class NaverAuthProvider : AuthProvider {
  private val logTag = "NaverAuthProvider"

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
            continuation.resume(
              AuthResult(
                authorizeToken = accessToken,
                refreshToken = NidOAuth.getRefreshToken().orEmpty(),
              ),
            )
          }

          override fun onFailure(errorCode: String, errorDesc: String) {
            if (!continuation.isActive) return
            Log.d(logTag, "Naver onFailure: code=$errorCode desc=$errorDesc")
            continuation.resumeWithException(mapError(errorCode, errorDesc))
          }
        },
      )
    }

  private fun mapError(errorCode: String, errorDesc: String): AuthError = when {
    errorCode.equals("user_cancel", ignoreCase = true) ||
      errorCode.equals("CLIENT_USER_CANCEL", ignoreCase = true) -> AuthError.Cancelled
    errorCode.contains("connection", ignoreCase = true) ||
      errorCode.contains("network", ignoreCase = true) -> AuthError.Network
    else -> AuthError.Unknown(IllegalStateException("Naver login failed: $errorCode $errorDesc"))
  }
}
