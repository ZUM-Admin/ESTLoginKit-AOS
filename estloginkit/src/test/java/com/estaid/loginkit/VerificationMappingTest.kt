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
package com.estaid.loginkit

import com.estaid.loginkit.internal.dto.VerificationCompleteStatus
import com.estaid.loginkit.model.AuthError
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** iOS `VerificationResultMappingTests` 대칭. */
class VerificationMappingTest {

  // region 완료 통지 → Result 매핑

  @Test
  fun `certified + token 이면 success`() {
    val result = VerificationCompleteStatus.result(status = "certified", token = "sso_abc")
    assertEquals("sso_abc", result.getOrNull()?.token)
  }

  @Test
  fun `certified 인데 token 없으면 VerificationFailed (세션 재수립 불가)`() {
    val result = VerificationCompleteStatus.result(status = "certified", token = null)
    assertTrue(result.exceptionOrNull() is AuthError.VerificationFailed)
  }

  @Test
  fun `cancelled 는 Cancelled`() {
    val result = VerificationCompleteStatus.result(status = "cancelled", token = null)
    assertTrue(result.exceptionOrNull() is AuthError.Cancelled)
  }

  @Test
  fun `error 는 VerificationFailed`() {
    val result = VerificationCompleteStatus.result(status = "error", token = null)
    assertTrue(result.exceptionOrNull() is AuthError.VerificationFailed)
  }

  @Test
  fun `알 수 없는 status 는 VerificationFailed`() {
    val result = VerificationCompleteStatus.result(status = "weird", token = "sso_abc")
    assertTrue(result.exceptionOrNull() is AuthError.VerificationFailed)
  }

  @Test
  fun `status 없음(파싱 실패) 은 VerificationFailed`() {
    val result = VerificationCompleteStatus.result(status = null, token = null)
    assertTrue(result.exceptionOrNull() is AuthError.VerificationFailed)
  }

  @Test
  fun `status 대소문자 무시`() {
    val result = VerificationCompleteStatus.result(status = "CERTIFIED", token = "sso_abc")
    assertEquals("sso_abc", result.getOrNull()?.token)
  }

  // endregion
}
