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
 * - [Network] : 네트워크/서버 오류 (verificationStatus 등)
 * - [Unauthorized] : accessToken 만료/무효 — 토큰 갱신 후 재시도 필요
 * - [Unknown] : 그 외 (원본 에러 wrapping)
 */
sealed class AuthError(
  message: String? = null,
  cause: Throwable? = null,
) : Exception(message, cause) {
  data object UnsupportedPlatform : AuthError("Unsupported login platform")

  data object Cancelled : AuthError("Login was cancelled")

  data object Network : AuthError("Network or server error")

  data object Unauthorized : AuthError("Access token expired or invalid")

  data class Unknown(val error: Throwable? = null) : AuthError(error?.message, error)
}
