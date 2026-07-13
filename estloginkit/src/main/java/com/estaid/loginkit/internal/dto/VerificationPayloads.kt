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

import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.model.AuthError
import com.estaid.loginkit.model.VerificationResult
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 본인인증 완료 통지 페이로드. (iOS `VerificationCompletePayload` 대칭)
 *
 * 브릿지(`onVerificationComplete`)와 callbackUrl 리다이렉트가 같은 status 값을 쓴다.
 * `{ "status": "certified", "token": "<ssoToken, certified일 때만>" }`
 */
@Serializable
internal data class VerificationCompletePayload(
  val status: String,
  val token: String? = null,
)

/**
 * 본인인증 종료 상태. (iOS `VerificationCompleteStatus` 대칭)
 *
 * callbackUrl 에서는 `?status=`, 브릿지에서는 payload 의 `status` 로 전달된다.
 */
internal enum class VerificationCompleteStatus(val rawValue: String) {
  /** 승격 완료 (CI 충돌 시 계정 병합까지 완료) */
  CERTIFIED("certified"),

  /** 사용자가 본인인증을 취소/중단 */
  CANCELLED("cancelled"),

  /** 승격 실패, 병합 실패, cert 조회 실패 등 */
  ERROR("error"),
  ;

  companion object {
    private fun from(rawValue: String?): VerificationCompleteStatus? =
      rawValue?.let { raw -> entries.firstOrNull { it.rawValue.equals(raw, ignoreCase = true) } }

    /**
     * 브릿지/callbackUrl 어느 경로로 들어왔든 동일하게 호스트 결과로 해석한다.
     * 알 수 없는 status 나 통지 누락(null)은 실패로 처리한다. (iOS 패리티)
     */
    fun result(status: String?, token: String?): Result<VerificationResult> =
      when (from(status)) {
        CERTIFIED -> {
          // certified 인데 토큰이 없으면 세션 재수립이 불가능하므로 성공으로 볼 수 없다.
          if (token.isNullOrBlank()) {
            EstLog.error("verification certified but token is missing")
            Result.failure(AuthError.VerificationFailed)
          } else {
            Result.success(VerificationResult(token))
          }
        }

        CANCELLED -> Result.failure(AuthError.Cancelled)

        ERROR, null -> {
          EstLog.error("verification failed — status: ${status ?: "null"}")
          Result.failure(AuthError.VerificationFailed)
        }
      }
  }
}

/** 본인인증 완료 페이로드 파싱. (iOS `WebViewMessage.decode` 대칭) */
internal object VerificationBridge {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  /** 문서에 없는 값이 와도 디코딩이 깨지지 않도록 status 는 String 으로 받고 해석은 매핑 단계에서 한다. */
  fun parse(message: String): VerificationCompletePayload? =
    runCatching { json.decodeFromString<VerificationCompletePayload>(message) }.getOrNull()
}
