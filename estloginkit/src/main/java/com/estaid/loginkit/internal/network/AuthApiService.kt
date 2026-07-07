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
 * 회원 본인인증 상태 조회 API.
 *
 * SDK 는 stateless 이므로 토큰을 보관하지 않는다 — accessToken 은 호출 시 호스트가 주입하며
 * `Authorization: Bearer {accessToken}` 헤더로 전달된다.
 *
 * 경로는 host 루트 기준(선행 `/`)이므로 `apiBaseUrl`(예: `https://api.estoneid.com`)의
 * `{scheme}://{host}/members/v1/certification/status` 로 해석된다.
 */
internal interface AuthApiService {
  @GET("/members/v1/certification/status")
  suspend fun verificationStatus(
    @Header("Authorization") authorization: String,
  ): CertificationStatusResponse
}

/**
 * 공통 응답: `{ "result": { "status": "CERTIFIED" | "UNCERTIFIED" }, "message": "" }`
 *
 * - `CERTIFIED`   : 본인인증을 완료한 회원
 * - `UNCERTIFIED` : 미인증 회원이거나 존재하지 않는 회원
 */
@Serializable
internal data class CertificationStatusResponse(
  @SerialName("result") val result: CertificationStatusResult = CertificationStatusResult(),
  @SerialName("message") val message: String = "",
)

@Serializable
internal data class CertificationStatusResult(
  @SerialName("status") val status: String = "UNCERTIFIED",
)
