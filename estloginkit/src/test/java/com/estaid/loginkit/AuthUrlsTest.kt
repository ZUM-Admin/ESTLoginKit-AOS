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

import com.estaid.loginkit.internal.AuthUrls
import com.estaid.loginkit.internal.SsoBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals

/** 본인인증 URL 빌더 검증. (iOS `verificationURL(callbackURL:)` 과 동일 형태여야 한다) */
class AuthUrlsTest {

  private val base = "https://test.estoneid.com"

  @Test
  fun `client_id 는 항상 포함된다`() {
    assertEquals(
      "$base/auth/verification?client_id=8941192",
      AuthUrls.verification(base, clientId = "8941192", callbackUrl = null),
    )
  }

  @Test
  fun `callbackURL 은 1회 인코딩돼 client_id 뒤에 붙는다`() {
    assertEquals(
      "$base/auth/verification?client_id=8941192" +
        "&callbackURL=https%3A%2F%2Ftest.estoneid.com%2Fauth%2Fapp-callback",
      AuthUrls.verification(base, "8941192", "$base/auth/app-callback"),
    )
  }

  @Test
  fun `빈 callbackURL 은 생략된다 - 빈 파라미터가 남지 않는다`() {
    assertEquals(
      "$base/auth/verification?client_id=1",
      AuthUrls.verification(base, "1", callbackUrl = "  "),
    )
  }

  @Test
  fun `부트스트랩 redirect_url 로 감싸면 가이드 예시와 동일해진다`() {
    // verificationUrl() → redirectUrlValue() → ssoLoginUrl() 경로가 정확히 1회 인코딩되는지
    // (client_id 추가 후에도 이중 인코딩이 생기지 않는지) 확인한다.
    val verification = AuthUrls.verification(base, "8941192", "$base/auth/app-callback")
    val url = SsoBootstrap.ssoLoginUrl(
      base,
      redirectUrl = SsoBootstrap.redirectUrlValue(verification),
      ssoToken = "TOKEN",
    )
    assertEquals(
      "$base/auth/sso-login?code=TOKEN&redirect_url=" +
        "%2Fauth%2Fverification%3Fclient_id%3D8941192%26callbackURL%3D" +
        "https%3A%2F%2Ftest.estoneid.com%2Fauth%2Fapp-callback",
      url,
    )
  }
}
