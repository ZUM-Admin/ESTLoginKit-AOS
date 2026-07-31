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
package com.estaid.loginkit.internal.dto

import com.estaid.loginkit.model.AuthResult
import com.estaid.loginkit.model.LoginPlatform
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 웹 → 네이티브 소셜 로그인 요청. */
@Serializable
internal data class SnsLoginRequestPayload(
  val type: String,
  val provider: String,
)

/** 네이티브 → 웹 성공 페이로드. */
@Serializable
internal data class SnsLoginSuccessPayload(
  val provider: String,
  val authorizeToken: String,
  val refreshToken: String = "",
  val ci: String = "",
  val email: String = "",
)

/** 네이티브 → 웹 에러 페이로드. */
@Serializable
internal data class SnsLoginErrorPayload(
  val code: String,
  val message: String,
  val provider: String = "",
)

/** 직렬화 + JS 콜백 스크립트 생성. */
internal object SnsLoginBridge {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  fun parseRequest(message: String): Result<SnsLoginRequestPayload> = runCatching {
    json.decodeFromString<SnsLoginRequestPayload>(message)
  }

  fun encodeSuccess(platform: LoginPlatform, result: AuthResult): String = json.encodeToString(
    SnsLoginSuccessPayload(
      provider = platform.rawValue,
      authorizeToken = result.authorizeToken,
      refreshToken = result.refreshToken,
      ci = result.ci,
      email = result.email,
    ),
  )

  fun encodeError(provider: String, code: String, message: String): String = json.encodeToString(
    SnsLoginErrorPayload(code = code, message = message, provider = provider),
  )

  fun successScript(payload: String): String = callbackScript("onNativeSnsLoginResult", payload)

  fun errorScript(payload: String): String = callbackScript("onNativeSnsLoginError", payload)

  private fun callbackScript(functionName: String, payload: String): String = """
    (function() {
      if (typeof window.$functionName === "function") {
        window.$functionName($payload);
      } else {
        console.warn("Missing window.$functionName");
      }
    })();
  """.trimIndent()
}
