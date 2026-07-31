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
 * 카카오 로그인 설정.
 *
 * @param appKey 카카오 네이티브 앱 키
 * @param customScheme debug/release 동일 키 사용 시 콜백 충돌 방지용 커스텀 스킴
 */
data class KakaoConfiguration(
  val appKey: String,
  val customScheme: String? = null,
)
