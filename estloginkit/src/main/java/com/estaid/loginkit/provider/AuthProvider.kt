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
package com.estaid.loginkit.provider

import androidx.activity.ComponentActivity
import com.estaid.loginkit.model.AuthResult

/**
 * 네이티브 로그인 구현체 프로토콜. (iOS `AuthProvider` 대칭)
 *
 * iOS 는 `login()` 이 인자가 없지만, Android 네이티브 SDK(카카오/네이버)는
 * Activity 컨텍스트를 요구하므로 [activity] 를 받는다.
 */
interface AuthProvider {
  /**
   * 실제 로그인 수행.
   * @return 성공 시 [AuthResult]
   * @throws com.estaid.loginkit.model.AuthError 취소/실패 시
   */
  suspend fun login(activity: ComponentActivity): AuthResult
}
