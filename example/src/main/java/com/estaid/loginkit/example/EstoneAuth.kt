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
package com.estaid.loginkit.example

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date

/**
 * ssoToken(OAuth code) → EST 통합회원 access/refresh 토큰 발급.
 *
 * SDK 가 아니라 **호스트(예제 앱) 책임**인 토큰 교환 계층이다. SDK 는 stateless 이므로
 * 이 계층이 ssoToken 을 access/refresh 로 바꾸고 저장한다.
 *
 * 흐름:
 *   1) POST /sso/access-token   {ssoToken, clientId}                    → accessToken
 *   2) POST /sso/refresh-token  {ssoToken, clientId} + Bearer(1의 결과) → refreshToken
 *   3) POST /refresh-sso        {accessToken, refreshToken}             → 둘 다 재발급
 */

/**
 * EST 통합회원 토큰.
 *
 * @param expiresIn 만료 "시각" (ms epoch). 초 단위 duration 이 아님에 주의.
 */
data class EstoneToken(
  val accessToken: String,
  val refreshToken: String,
  val expiresIn: Long,
) {
  val expiryDate: Date get() = Date(expiresIn)
}

/** 토큰 교환 실패 에러. */
class EstoneAuthException(message: String) : IOException(message)

/**
 * 예제 앱의 호스트측 토큰 교환. 백엔드 주소·클라이언트 ID 는 [BuildConfig] 로 주입된다.
 * (소스에 하드코딩 없음)
 */
object EstoneAuth {
  private val baseUrl: String get() = "https://${BuildConfig.EST_API_HOST}/auth"
  private val clientId: String get() = BuildConfig.EST_CLIENT_ID

  /** ssoToken(OAuth code) → access/refresh 2단계 발급. */
  suspend fun issueToken(ssoToken: String): EstoneToken = withContext(Dispatchers.IO) {
    val body = JSONObject()
      .put("ssoToken", ssoToken)
      .put("clientId", clientId)
      .toString()

    val accessResult = post("/sso/access-token", body)
    val accessToken = accessResult.optString("accessToken")
    if (accessToken.isEmpty()) throw EstoneAuthException("missing field: accessToken")

    val refreshResult = post("/sso/refresh-token", body, bearer = accessToken)
    val refreshToken = refreshResult.optString("refreshToken")
    if (refreshToken.isEmpty()) throw EstoneAuthException("missing field: refreshToken")

    val expiresIn = if (refreshResult.has("expiresIn")) {
      refreshResult.optLong("expiresIn")
    } else {
      accessResult.optLong("expiresIn")
    }

    EstoneToken(accessToken = accessToken, refreshToken = refreshToken, expiresIn = expiresIn)
  }

  /** access/refresh 둘 다 재발급. */
  suspend fun renewToken(token: EstoneToken): EstoneToken = withContext(Dispatchers.IO) {
    val body = JSONObject()
      .put("accessToken", token.accessToken)
      .put("refreshToken", token.refreshToken)
      .toString()

    val result = post("/refresh-sso", body)
    val accessToken = result.optString("accessToken")
    if (accessToken.isEmpty()) throw EstoneAuthException("missing field: accessToken")
    val refreshToken = result.optString("refreshToken")
    if (refreshToken.isEmpty()) throw EstoneAuthException("missing field: refreshToken")

    EstoneToken(
      accessToken = accessToken,
      refreshToken = refreshToken,
      expiresIn = result.optLong("expiresIn"),
    )
  }

  /**
   * POST JSON 요청 후 `result` 객체를 반환한다. 응답 스키마: `{"result": {...}}`.
   * 비-2xx 응답이면 상태 + 본문으로 [EstoneAuthException] 을 던진다.
   */
  private fun post(path: String, jsonBody: String, bearer: String? = null): JSONObject {
    val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
      requestMethod = "POST"
      doOutput = true
      setRequestProperty("Content-Type", "application/json")
      setRequestProperty("Accept", "application/json")
      if (bearer != null) setRequestProperty("Authorization", "Bearer $bearer")
      connectTimeout = 15_000
      readTimeout = 15_000
    }
    return try {
      connection.outputStream.use { it.write(jsonBody.toByteArray(Charsets.UTF_8)) }

      val status = connection.responseCode
      val stream = if (status in 200..299) connection.inputStream else connection.errorStream
      val responseBody = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

      if (status !in 200..299) {
        throw EstoneAuthException("HTTP $status: $responseBody")
      }
      JSONObject(responseBody).getJSONObject("result")
    } finally {
      connection.disconnect()
    }
  }
}

/**
 * EstoneToken 을 SharedPreferences 에 저장한다.
 * 예제 앱이므로 SharedPreferences 를 쓴다. 실서비스는 EncryptedSharedPreferences/Keystore 권장.
 */
class TokenStore(context: Context) {
  private val prefs = context.applicationContext
    .getSharedPreferences("estone.tokens", Context.MODE_PRIVATE)

  fun save(token: EstoneToken) {
    prefs.edit()
      .putString(KEY_ACCESS, token.accessToken)
      .putString(KEY_REFRESH, token.refreshToken)
      .putLong(KEY_EXPIRES, token.expiresIn)
      .apply()
  }

  fun load(): EstoneToken? {
    val access = prefs.getString(KEY_ACCESS, null) ?: return null
    val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
    return EstoneToken(
      accessToken = access,
      refreshToken = refresh,
      expiresIn = prefs.getLong(KEY_EXPIRES, 0L),
    )
  }

  fun clear() {
    prefs.edit()
      .remove(KEY_ACCESS)
      .remove(KEY_REFRESH)
      .remove(KEY_EXPIRES)
      .apply()
  }

  private companion object {
    const val KEY_ACCESS = "estone.accessToken"
    const val KEY_REFRESH = "estone.refreshToken"
    const val KEY_EXPIRES = "estone.expiresIn"
  }
}
