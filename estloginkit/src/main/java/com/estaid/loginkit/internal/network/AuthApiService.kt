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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Header

/**
 * 본인인증 여부 조회 API. (README_v2 §본인인증)
 *
 * SDK 는 stateless 이므로 토큰을 보관하지 않는다 — accessToken 은 호출 시 호스트가 주입하며
 * `Authorization: Bearer {accessToken}` 헤더로 전달된다.
 *
 * 주의: 엔드포인트 경로/응답 JSON 은 백엔드 스펙 (미정). 경로는 placeholder 이다.
 */
internal interface AuthApiService {
  @GET("verification/status") // (미정) — 스펙 확정 시 경로 변경
  suspend fun verificationStatus(
    @Header("Authorization") authorization: String,
  ): VerificationStatusResponse
}

/** (미정) — 응답 필드는 백엔드 스펙 확정 후 조정. */
@Serializable
internal data class VerificationStatusResponse(
  @SerialName("isVerified") val isVerified: Boolean = false,
  @SerialName("verifiedAt") val verifiedAt: String? = null,
  @SerialName("expiresAt") val expiresAt: String? = null,
)
