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
package com.estaid.loginkit.internal.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit

/**
 * 본인인증 조회용 Retrofit 클라이언트.
 *
 * stateless: JWT 서명/vendor 헤더를 생성하지 않으며, 토큰은 [AuthApiService] 호출 시
 * 호스트가 주입하는 `Authorization: Bearer` 헤더로만 전달된다.
 */
internal class AuthNetworkClient(
  private val authApiBaseUrl: String,
  private val debugMode: Boolean,
) {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  val authApiService: AuthApiService by lazy {
    createRetrofit().create(AuthApiService::class.java)
  }

  private fun createRetrofit(): Retrofit {
    val okHttpClient = OkHttpClient.Builder()
      .readTimeout(15, TimeUnit.SECONDS)
      .connectTimeout(15, TimeUnit.SECONDS)
      .apply {
        if (debugMode) {
          addInterceptor(
            HttpLoggingInterceptor().apply {
              level = HttpLoggingInterceptor.Level.BODY
              // accessToken이 로그에 남지 않도록 마스킹 (ssoToken 발급 등 Bearer 요청)
              redactHeader("Authorization")
            },
          )
        }
      }
      .build()

    // Retrofit 은 base URL 이 '/' 로 끝나야 한다. apiBaseUrl 은 host 만 오므로(예: https://api.estoneid.com)
    // 보정한다. 실제 경로는 [AuthApiService] 의 선행 '/' 경로가 host 루트 기준으로 대체하므로 base path 는 무시된다.
    val normalizedBaseUrl = if (authApiBaseUrl.endsWith("/")) authApiBaseUrl else "$authApiBaseUrl/"

    return Retrofit.Builder()
      .baseUrl(normalizedBaseUrl)
      .client(okHttpClient)
      .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
      .build()
  }
}
