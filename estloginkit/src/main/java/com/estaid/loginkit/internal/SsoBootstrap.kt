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

import java.net.URI
import java.net.URLEncoder

/**
 * SSO 부트스트랩 URL 빌더. (iOS `SSOToken.swift`의 `ssoLoginURL` 대칭)
 *
 * `GET {baseUrl}/auth/sso-login?code={ssoToken}&redirect_url={URL인코딩된 내부 경로}`
 *
 * 웹은 code를 검증해 자체 세션 쿠키를 수립한 뒤 redirect_url로 이동시키므로,
 * 쿠키가 없는 기기/상태에서도 마이페이지·본인인증 웹뷰를 열 수 있다.
 * code가 만료 등으로 실패하면 웹이 기존 세션을 정리하고 로그인 화면으로 보내며,
 * 로그인 후 redirect_url로 복귀하므로 앱의 별도 처리는 필요 없다.
 */
internal object SsoBootstrap {

  /**
   * SSO 부트스트랩 URL을 만든다.
   *
   * @param redirectUrl 세션 수립 후 이동할 est 내부 경로(자체 쿼리 포함, 미인코딩 원본).
   *   null이면 생략되어 홈(/)으로 이동한다. 외부 URL은 웹이 홈으로 대체한다.
   */
  fun ssoLoginUrl(baseUrl: String, redirectUrl: String?, ssoToken: String): String {
    val query = buildString {
      append("code=").append(queryEncoded(ssoToken))
      if (redirectUrl != null) {
        append("&redirect_url=").append(queryEncoded(redirectUrl))
      }
    }
    return "$baseUrl/auth/sso-login?$query"
  }

  /**
   * 목적지 URL에서 redirect_url 값(path + query)을 추출한다. 예: `/auth/verification?client_id=1`
   *
   * 디코딩된 path/query를 쓴다 — `verificationUrl()`이 callbackURL을 이미 인코딩해 두므로,
   * 인코딩된 형태를 그대로 다시 인코딩하면 이중 인코딩이 된다(가이드는 "1회 인코딩").
   */
  fun redirectUrlValue(url: String): String {
    val uri = URI(url)
    val query = uri.query?.let { "?$it" }.orEmpty()
    return uri.path + query
  }

  /**
   * 쿼리 값 인코딩. 토큰은 AES256 암호화 문자열이라 `+` `=` `/` 등을 포함할 수 있고
   * `+`를 그대로 두면 서버가 공백으로 해석하므로 전부 퍼센트 인코딩한다.
   * (URLEncoder는 공백을 `+`로 바꾸므로 `%20`으로 보정해 encodeURIComponent와 맞춘다)
   */
  private fun queryEncoded(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
