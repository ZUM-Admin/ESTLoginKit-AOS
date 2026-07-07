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
 * 회원 본인인증 상태 조회 결과. (iOS `VerificationStatus` 대칭)
 *
 * 응답은 통합회원 계정 단위의 인증 완료 여부(`CERTIFIED`/`UNCERTIFIED`)만 제공한다.
 * [isVerified] 가 `true` 이면 `CERTIFIED` 이다.
 */
data class VerificationStatus(
  val isVerified: Boolean,
)
