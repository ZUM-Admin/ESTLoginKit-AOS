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
package com.estaid.loginkit.internal.webview

import com.estaid.loginkit.internal.EstLog
import com.estaid.loginkit.internal.dto.VerificationCompleteStatus
import com.estaid.loginkit.model.VerificationResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 본인인증 완료 통지를 호스트에 1회만 전달한다.
 *
 * 통지는 callbackUrl 리다이렉트 한 경로로만 들어오지만, 웹이 리다이렉트를 재시도해
 * 여러 번 매칭될 수 있으므로 첫 전달만 통과시킨다. (임의 스레드 호출 대비 원자적으로 가드한다)
 */
internal class VerificationResultDelivery(
  private val onResult: (Result<VerificationResult>) -> Unit,
) {
  private val delivered = AtomicBoolean(false)

  /** callbackUrl 경로: `<callbackUrl>?status=...&code=<ssoToken>`. */
  fun fromCallback(status: String?, code: String?) = deliver(status, code)

  private fun deliver(status: String?, token: String?) {
    if (!delivered.compareAndSet(false, true)) {
      EstLog.debug("verification result already delivered — ignoring")
      return
    }
    onResult(VerificationCompleteStatus.result(status, token))
  }
}
