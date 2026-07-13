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
package com.estaid.loginkit.model

/**
 * SDK 공개 에러 타입. (iOS `AuthError` 대칭)
 *
 * - [UnsupportedPlatform] : 지원하지 않는 로그인 플랫폼
 * - [Cancelled] : 사용자 취소
 * - [NotInitialized] : `initialize(...)` 미호출 — 설정(환경/clientId)이 없음
 * - [VerificationFailed] : 본인인증 승격/병합 실패, 또는 완료 통지를 해석할 수 없음
 * - [Server] : 서버가 2xx 가 아닌 상태 코드로 응답 (`statusCode == 401` 이면 토큰 갱신 후 재시도)
 * - [Unknown] : 그 외 (네트워크 오류 포함, 원본 에러 wrapping)
 */
sealed class AuthError(
  message: String? = null,
  cause: Throwable? = null,
) : Exception(message, cause) {
  data object UnsupportedPlatform : AuthError("Unsupported login platform")

  data object Cancelled : AuthError("Login was cancelled")

  data object NotInitialized : AuthError("EstLoginManager is not initialized. Call initialize() first.")

  data object VerificationFailed : AuthError("Identity verification failed")

  data class Server(val statusCode: Int) : AuthError("Server responded with status $statusCode")

  data class Unknown(val error: Throwable? = null) : AuthError(error?.message, error)
}
