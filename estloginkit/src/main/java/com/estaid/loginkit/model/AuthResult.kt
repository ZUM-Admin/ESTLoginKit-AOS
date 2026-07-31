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
 * 네이티브 소셜 로그인 결과.
 *
 * SDK 는 stateless 이므로 이 토큰을 보관하지 않는다 — 호스트가 받아서
 * accessToken/refreshToken 교환·저장을 직접 수행한다.
 */
data class AuthResult(
  val authorizeToken: String,
  val refreshToken: String = "",
  val ci: String = "",
  val email: String = "",
)
