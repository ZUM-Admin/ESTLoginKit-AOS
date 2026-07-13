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

import android.webkit.JavascriptInterface
import com.estaid.loginkit.internal.EstLog

/**
 * 웹 → 네이티브 JS 브릿지. (iOS `WKScriptMessageHandler` 대칭)
 *
 * 웹 페이지에서 `AndroidInterface.<method>(...)` 형태로 호출한다.
 */
internal class AuthJsInterface(
  private val onSnsLoginRequested: (String) -> Unit,
  private val onPasswordChanged: () -> Unit,
  private val onAccountDeleted: () -> Unit,
  private val onVerificationComplete: ((String) -> Unit)? = null,
) {
  @JavascriptInterface
  fun requestSnsLogin(message: String) {
    EstLog.debug("[bridge] ${WebViewMessage.REQUEST_SNS_LOGIN.rawValue}: $message")
    onSnsLoginRequested(message)
  }

  @JavascriptInterface
  fun onLoginComplete(message: String) {
    // 관찰/통지용 — dismiss/redirect 는 callbackUrl/state 매칭으로 처리됨.
    EstLog.debug("[bridge] ${WebViewMessage.ON_LOGIN_COMPLETE.rawValue}: $message")
  }

  @JavascriptInterface
  fun onPasswordChanged() {
    EstLog.debug("[bridge] ${WebViewMessage.ON_PASSWORD_CHANGED.rawValue}")
    onPasswordChanged.invoke()
  }

  @JavascriptInterface
  fun onAccountDeleted() {
    EstLog.debug("[bridge] ${WebViewMessage.ON_ACCOUNT_DELETED.rawValue}")
    onAccountDeleted.invoke()
  }

  @JavascriptInterface
  fun onVerificationComplete(message: String) {
    EstLog.debug("[bridge] ${WebViewMessage.ON_VERIFICATION_COMPLETE.rawValue}: $message")
    onVerificationComplete?.invoke(message)
  }
}
