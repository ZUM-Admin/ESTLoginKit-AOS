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
 * 본인인증 화면 완료 결과.
 *
 * [token] 은 본인인증 후 재발급된 ssoToken 이다. CI 충돌로 계정이 병합되면 웹뷰 안의 세션이
 * 다른 계정으로 바뀌어 있을 수 있으므로, 호스트는 이 토큰으로 세션을 재수립해야 병합된 계정과
 * 상태가 맞는다.
 *
 * 추가 필드(ci/di 등)는 백엔드 스펙 (미정).
 */
data class VerificationResult(
  val token: String,
)
