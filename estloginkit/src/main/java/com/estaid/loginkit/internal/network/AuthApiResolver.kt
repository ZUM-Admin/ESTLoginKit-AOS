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
package com.estaid.loginkit.internal.network

import android.net.Uri

/**
 * 인증 서버(AUTH_API) base 를 [baseUrl] 에 대응해 결정한다. (README_v2 §설정)
 *
 * - 운영(`estoneid.com`)      → `https://api.estoneid.com/auth/`
 * - 개발/스테이징(`test`/`dev`) → `https://dev-api.estoneid.com/auth/`
 *
 * 호스트가 호환되지 않는 커스텀 baseUrl 을 쓰는 경우 운영 AUTH_API 로 폴백한다.
 */
internal object AuthApiResolver {
  private const val PROD = "https://api.estoneid.com/auth/"
  private const val DEV = "https://dev-api.estoneid.com/auth/"

  fun resolveAuthApi(baseUrl: String): String {
    val host = runCatching { Uri.parse(baseUrl).host }.getOrNull().orEmpty().lowercase()
    val isNonProd = host.startsWith("test.") || host.startsWith("dev.") ||
      host.contains("test") || host.contains("dev") || host.contains("stage")
    return if (isNonProd) DEV else PROD
  }
}
