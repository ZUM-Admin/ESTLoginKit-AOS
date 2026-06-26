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
 * 네이버 로그인 설정. (iOS `NaverConfiguration` 대칭)
 *
 * @param appName 네이버 로그인 화면에 표시할 앱 이름
 * @param clientId 네이버 클라이언트 ID
 * @param clientSecret 네이버 클라이언트 시크릿
 * @param urlScheme iOS 콜백용 URL 스킴 — Android 에서는 사용하지 않으며 크로스플랫폼 파라미터 정합을 위해 둔다.
 */
data class NaverConfiguration(
  val appName: String,
  val clientId: String,
  val clientSecret: String,
  val urlScheme: String? = null,
)
