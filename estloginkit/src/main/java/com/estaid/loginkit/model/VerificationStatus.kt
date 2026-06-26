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
 * 본인인증 여부 조회 결과. (iOS `VerificationStatus` 대칭)
 *
 * 주의: 응답 필드(verifiedAt/expiresAt 타입·존재 여부)는 백엔드 스펙 (미정).
 * 스펙 확정 시 타입이 Date 계열로 바뀔 수 있다.
 */
data class VerificationStatus(
  val isVerified: Boolean,
  val verifiedAt: String? = null,
  val expiresAt: String? = null,
)
