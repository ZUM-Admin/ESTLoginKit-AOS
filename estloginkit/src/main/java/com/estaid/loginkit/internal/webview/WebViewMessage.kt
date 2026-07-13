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

/**
 * 웹 → 네이티브 JS 브릿지 메시지 종류. (iOS `WebViewMessage` 대칭)
 */
internal enum class WebViewMessage(val rawValue: String) {
  REQUEST_SNS_LOGIN("requestSnsLogin"),
  ON_LOGIN_COMPLETE("onLoginComplete"),
  ON_PASSWORD_CHANGED("onPasswordChanged"),
  ON_ACCOUNT_DELETED("onAccountDeleted"),

  /** 본인인증 완료 통지. 로그인용 [ON_LOGIN_COMPLETE] 와 분리된 별도 메서드다. */
  ON_VERIFICATION_COMPLETE("onVerificationComplete"),
  ;

  companion object {
    fun from(rawValue: String): WebViewMessage? = entries.firstOrNull { it.rawValue == rawValue }
  }
}
