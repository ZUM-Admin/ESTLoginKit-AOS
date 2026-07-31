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
package com.estaid.loginkit.model

/**
 * 네이티브 로그인 플랫폼.
 *
 * 구글/애플은 네이티브 SDK 를 쓰지 않고 WebView OAuth 경로로 일원화되므로 여기 포함되지 않는다.
 */
enum class LoginPlatform(val rawValue: String) {
  KAKAO("kakao"),
  NAVER("naver"),
  ;

  companion object {
    fun from(rawValue: String): LoginPlatform? =
      entries.firstOrNull { it.rawValue.equals(rawValue, ignoreCase = true) }
  }
}
