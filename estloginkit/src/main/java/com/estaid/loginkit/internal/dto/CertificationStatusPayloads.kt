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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `GET /members/v1/certification/status` 응답. (iOS `CertificationStatusResponseDTO` 대칭)
 *
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
