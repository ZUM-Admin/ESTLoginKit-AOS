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

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import com.estaid.loginkit.internal.EstLog

/**
 * 웹 → 네이티브 JS 브릿지.
 *
 * 웹 페이지에서 `AndroidInterface.<method>(...)` 형태로 호출한다.
 *
 * `@JavascriptInterface` 메서드는 **JavaBridge 스레드**에서 호출된다. 호스트 콜백은 화면 전환·
 * 토큰 재발급 등 UI 작업을 하는 게 보통이므로, 모든 통지를 메인 스레드로 넘겨서 전달한다.
 * (이게 없으면 호스트 쪽에서 조용히 무시되거나 wrong-thread 예외로 죽는다)
 */
internal class AuthJsInterface(
  private val onSnsLoginRequested: (String) -> Unit,
  private val onLogoutRequested: () -> Unit,
  private val onPasswordChanged: () -> Unit,
  private val onAccountDeleted: () -> Unit,
) {
  private val mainHandler = Handler(Looper.getMainLooper())

  private fun onMain(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
  }

  @JavascriptInterface
  fun requestSnsLogin(message: String) {
    EstLog.debug("[bridge] ${WebViewMessage.REQUEST_SNS_LOGIN.rawValue}: $message")
    onMain { onSnsLoginRequested(message) }
  }

  /**
   * "다른 계정으로 로그인" 등 — 네이티브 SNS SDK 에 캐싱된 로그인 상태를 지워달라는 요청.
   *
   * 이게 없으면 다음 [requestSnsLogin] 에서 카카오/네이버 SDK 가 기존 토큰을 재사용해
   * 계정 선택창 없이 같은 계정으로 조용히 로그인된다.
   * 웹 세션 쿠키와 호스트 토큰은 건드리지 않는다(각자 책임).
   */
  @JavascriptInterface
  fun requestLogout() {
    EstLog.debug("[bridge] ${WebViewMessage.REQUEST_LOGOUT.rawValue}")
    onMain { onLogoutRequested.invoke() }
  }

  /** 웹이 페이로드를 실어 부르는 경우 대비. */
  @JavascriptInterface
  fun requestLogout(message: String?) {
    EstLog.debug("[bridge] ${WebViewMessage.REQUEST_LOGOUT.rawValue}: $message")
    onMain { onLogoutRequested.invoke() }
  }

  @JavascriptInterface
  fun onLoginComplete(message: String?) {
    // 관찰/통지용 — dismiss/redirect 는 callbackUrl/state 매칭으로 처리됨.
    EstLog.debug("[bridge] ${WebViewMessage.ON_LOGIN_COMPLETE.rawValue}: $message")
  }

  /** 웹이 인자 없이 부르는 경우 대비. */
  @JavascriptInterface
  fun onLoginComplete() {
    EstLog.debug("[bridge] ${WebViewMessage.ON_LOGIN_COMPLETE.rawValue}")
  }

  @JavascriptInterface
  fun onPasswordChanged() {
    EstLog.debug("[bridge] ${WebViewMessage.ON_PASSWORD_CHANGED.rawValue}")
    onMain { onPasswordChanged.invoke() }
  }

  /**
   * 웹은 페이로드(`"{}"`)를 실어 인자 1개로 호출한다 — 인자 없는 오버로드만 두면
   * arity 불일치로 호출 자체가 예외가 되고, 웹은 `failed to invoke ... bridge` 만 남긴 채 조용히 실패한다.
   * 페이로드는 현재 통지에 쓰이지 않으므로 로그만 남기고 버린다.
   */
  @JavascriptInterface
  fun onPasswordChanged(message: String?) {
    EstLog.debug("[bridge] ${WebViewMessage.ON_PASSWORD_CHANGED.rawValue}: $message")
    onMain { onPasswordChanged.invoke() }
  }

  @JavascriptInterface
  fun onAccountDeleted() {
    EstLog.debug("[bridge] ${WebViewMessage.ON_ACCOUNT_DELETED.rawValue}")
    onMain { onAccountDeleted.invoke() }
  }

  /** 웹의 인자 1개 호출용. [onPasswordChanged] 오버로드와 같은 이유. */
  @JavascriptInterface
  fun onAccountDeleted(message: String?) {
    EstLog.debug("[bridge] ${WebViewMessage.ON_ACCOUNT_DELETED.rawValue}: $message")
    onMain { onAccountDeleted.invoke() }
  }
}
