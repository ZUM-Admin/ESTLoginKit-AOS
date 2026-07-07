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

/**
 * 통합회원(estoneid) 실행 환경. (iOS `ESTEnvironment` 대칭)
 *
 * 환경마다 로그인 웹 host 와 API host 가 쌍으로 다르므로, 앱은 환경만 선택하고
 * SDK 가 두 URL 을 소유한다 — 웹/API host 불일치를 원천 차단한다.
 */
enum class EstEnvironment {
  PRODUCTION,
  DEVELOPMENT;

  /** 로그인/마이페이지 등 웹 화면 base URL. */
  val webBaseUrl: String
    get() = when (this) {
      PRODUCTION -> "https://estoneid.com"
      DEVELOPMENT -> "https://dev.estoneid.com"
    }

  /** 본인인증 등 REST API base URL. */
  val apiBaseUrl: String
    get() = when (this) {
      PRODUCTION -> "https://api.estoneid.com"
      DEVELOPMENT -> "https://dev-api.estoneid.com"
    }
}
