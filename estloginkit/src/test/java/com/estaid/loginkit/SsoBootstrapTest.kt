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

import com.estaid.loginkit.internal.SsoBootstrap
import kotlin.test.Test
import kotlin.test.assertEquals

/** SSO 부트스트랩 URL 빌더 검증. */
class SsoBootstrapTest {

  private val base = "https://test.estoneid.com"

  @Test
  fun `GET sso-login에 code와 redirect_url 쿼리가 담긴다`() {
    val url = SsoBootstrap.ssoLoginUrl(base, redirectUrl = "/mypage/setting", ssoToken = "abc123")
    assertEquals("$base/auth/sso-login?code=abc123&redirect_url=%2Fmypage%2Fsetting", url)
  }

  @Test
  fun `redirect_url 생략 시 code만 담긴다 - 웹이 홈으로 이동`() {
    val url = SsoBootstrap.ssoLoginUrl(base, redirectUrl = null, ssoToken = "abc123")
    assertEquals("$base/auth/sso-login?code=abc123", url)
  }

  @Test
  fun `본인인증 redirect_url이 가이드 예시와 동일하게 1회 인코딩된다`() {
    val url = SsoBootstrap.ssoLoginUrl(
      base,
      redirectUrl = "/auth/verification?client_id=8941192&callbackURL=https://test.estoneid.com/auth/app-callback",
      ssoToken = "TOKEN",
    )
    assertEquals(
      "$base/auth/sso-login?code=TOKEN&redirect_url=" +
        "%2Fauth%2Fverification%3Fclient_id%3D8941192%26callbackURL%3D" +
        "https%3A%2F%2Ftest.estoneid.com%2Fauth%2Fapp-callback",
      url,
    )
  }

  @Test
  fun `토큰 특수문자는 퍼센트 인코딩된다`() {
    // +를 그대로 두면 서버가 공백으로 해석한다 (AES256 토큰은 + = / 포함 가능)
    val url = SsoBootstrap.ssoLoginUrl(base, redirectUrl = null, ssoToken = "AES+base64/pad==")
    assertEquals("$base/auth/sso-login?code=AES%2Bbase64%2Fpad%3D%3D", url)
  }

  @Test
  fun `redirectUrlValue - path와 query 유지`() {
    assertEquals(
      "/auth/verification?client_id=1",
      SsoBootstrap.redirectUrlValue("https://test.estoneid.com/auth/verification?client_id=1"),
    )
  }

  @Test
  fun `redirectUrlValue - 쿼리 없는 URL은 path만`() {
    assertEquals(
      "/mypage/setting",
      SsoBootstrap.redirectUrlValue("https://test.estoneid.com/mypage/setting"),
    )
  }

  @Test
  fun `redirectUrlValue - 인코딩된 쿼리 값은 디코딩된다 - 이중 인코딩 방지`() {
    // verificationUrl()은 callbackURL을 이미 인코딩해 두므로 여기서 디코딩해야
    // ssoLoginUrl의 1회 인코딩과 합쳐 정확히 1회 인코딩이 된다.
    val encoded = "https://test.estoneid.com/auth/verification" +
      "?client_id=1&callbackURL=https%3A%2F%2Ftest.estoneid.com%2Fauth%2Fapp-callback"
    assertEquals(
      "/auth/verification?client_id=1&callbackURL=https://test.estoneid.com/auth/app-callback",
      SsoBootstrap.redirectUrlValue(encoded),
    )
  }
}
