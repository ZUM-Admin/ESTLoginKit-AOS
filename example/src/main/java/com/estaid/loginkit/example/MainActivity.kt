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
package com.estaid.loginkit.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.estaid.loginkit.EstLoginManager
import com.estaid.loginkit.model.LoginPlatform
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent {
      MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
          ExampleScreen(activity = this)
        }
      }
    }
  }
}

@Composable
private fun ExampleScreen(activity: ComponentActivity) {
  val scope = rememberCoroutineScope()
  var status by remember { mutableStateOf("Idle") }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    horizontalAlignment = Alignment.CenterHorizontally,
  ) {
    Text(text = "ESTLoginKit Example")
    Text(text = "Status: $status")

    Button(onClick = {
      scope.launch {
        status = "Web login..."
        val result = EstLoginManager.startWebLogin(activity)
        status = result.fold(
          onSuccess = { token -> "ssoToken=${token ?: "(none)"}" },
          onFailure = { "Failed: ${it.message}" },
        )
      }
    }) { Text("WebView 로그인") }

    Button(onClick = {
      scope.launch {
        status = try {
          val r = EstLoginManager.login(activity, LoginPlatform.KAKAO)
          "Kakao token=${r.authorizeToken.take(12)}..."
        } catch (e: Exception) {
          "Kakao failed: ${e.message}"
        }
      }
    }) { Text("카카오 로그인") }

    Button(onClick = {
      scope.launch {
        EstLoginManager.logout()
        status = "Logged out"
        Toast.makeText(activity, "Logged out", Toast.LENGTH_SHORT).show()
      }
    }) { Text("로그아웃") }
  }
}
