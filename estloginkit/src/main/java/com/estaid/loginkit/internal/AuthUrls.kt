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
package com.estaid.loginkit.internal

import java.net.URLEncoder

/**
 * 웹 URL 빌더 중 Context·설정 상태에 의존하지 않는 순수 로직.
 *
 * `EstLoginManager` 는 초기화(Context 필요)를 거쳐야 URL 을 만들 수 있어 JVM 유닛테스트로
 * 검증할 수 없으므로, 문자열 조립만 여기로 분리해 테스트 가능하게 둔다.
 */
internal object AuthUrls {

  /**
   * 본인인증 화면 URL.
   *
   * `{baseUrl}/auth/verification?client_id={clientId}[&callbackURL={URL인코딩}]`
   *
   * `client_id` 는 필수다 — 웹이 어느 서비스의 인증인지 판별하는 값이라 빠지면 화면이 열리지 않는다.
   */
  fun verification(baseUrl: String, clientId: String, callbackUrl: String?): String {
    val query = buildList {
      add("client_id=${queryEncoded(clientId)}")
      if (!callbackUrl.isNullOrBlank()) add("callbackURL=${queryEncoded(callbackUrl)}")
    }.joinToString("&")
    return "$baseUrl/auth/verification?$query"
  }

  /** SsoBootstrap 의 쿼리 인코딩과 동일 규칙 — 공백을 `+` 대신 `%20` 으로 보정한다. */
  private fun queryEncoded(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
