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
import com.estaid.loginkit.internal.dto.VerificationBridge
import com.estaid.loginkit.internal.dto.VerificationCompleteStatus
import com.estaid.loginkit.model.VerificationResult
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 본인인증 완료 통지를 호스트에 1회만 전달한다. (iOS `hasDeliveredVerificationResult` 가드 대칭)
 *
 * 웹은 브릿지(`onVerificationComplete`) → (미등록 시) callbackUrl 리다이렉트 순으로 통지하므로,
 * 브릿지가 등록된 상태에서 웹이 리다이렉트까지 수행해도 호스트가 결과를 두 번 받지 않도록
 * 첫 전달만 통과시킨다. (브릿지는 임의 스레드에서 호출될 수 있어 원자적으로 가드한다)
 */
internal class VerificationResultDelivery(
  private val onResult: (Result<VerificationResult>) -> Unit,
) {
  private val delivered = AtomicBoolean(false)

  /** 브릿지 경로: JSON 문자열 페이로드. 파싱 실패 시에도 실패 결과를 전달해 화면이 열린 채 남지 않게 한다. */
  fun fromBridge(message: String) {
    val payload = VerificationBridge.parse(message)
    if (payload == null) {
      EstLog.error("verification bridge payload parse failed")
      deliver(status = null, token = null)
    } else {
      deliver(status = payload.status, token = payload.token)
    }
  }

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
