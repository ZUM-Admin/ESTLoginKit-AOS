# ESTLoginKit (Android) — 연동 가이드

## 1. 의존성 추가

### 공개 배포 — JitPack (별도 인증 없이 소비)

퍼블릭 GitHub 저장소(`ZUM-Admin/ESTLoginKit-AOS`)를 JitPack이 태그 기준으로 빌드·배포합니다.
소비앱은 토큰 인증 없이 바로 받을 수 있습니다.

```kotlin
// settings.gradle.kts — dependencyResolutionManagement.repositories
google()
mavenCentral()                                                  // 네이버 SDK(com.navercorp.nid:oauth)
maven("https://jitpack.io")                                     // ESTLoginKit
maven("https://devrepo.kakao.com/nexus/content/groups/public/") // 카카오 SDK(com.kakao.sdk:v2-user)
```

```kotlin
// app/build.gradle.kts
dependencies {
  implementation("com.github.ZUM-Admin:ESTLoginKit-AOS:2.0.1")
}
```

> **카카오/네이버 SDK 는 직접 추가하지 마세요** — ESTLoginKit 의 전이 의존성으로 자동 포함됩니다.
> 대신 그 전이 의존성이 **소비 앱의 저장소**에서 해석되므로(현대 Gradle 은 `FAIL_ON_PROJECT_REPOS`
> 로 라이브러리 저장소를 상속하지 않음), 위 저장소를 소비 앱 `settings.gradle.kts` 에 선언해야 합니다:
> - `mavenCentral()`·`google()` — 대부분 프로젝트에 이미 있음 (네이버 SDK 포함)
> - `devrepo.kakao.com` — 카카오 SDK 는 **카카오 전용 저장소에만** 있어 반드시 추가 (SDK 가 카카오에
>   무조건 의존하므로 카카오 로그인 미사용이어도 필요)
> - `jitpack.io` — ESTLoginKit 본체
>
> 배포는 **git 태그 push**만으로 이뤄집니다(별도 publish 태스크 불필요). 태그를 올리면
> JitPack이 `:estloginkit` 모듈만 빌드해 발행합니다(`jitpack.yml` 참고 — 예제 앱은 제외).
> 배포된 버전 목록·빌드 상태는 `https://jitpack.io/#ZUM-Admin/ESTLoginKit-AOS` 에서 확인하세요.

### 전환기 (로컬 개발) — composite build (`includeBuild`)

이 레포는 **자체 버전 카탈로그**(`gradle/libs.versions.toml`)를 쓰므로, `projectDir` 로 include 하면
호스트의 카탈로그와 alias 가 충돌합니다. 따라서 **composite build + 명시적 substitution** 으로 소비합니다.

```kotlin
// 호스트 settings.gradle.kts (top-level)
includeBuild("<이 레포까지의 상대경로>/ESTLoginKit-Android") {
    dependencySubstitution {
        substitute(module("com.estaid:loginkit")).using(project(":estloginkit"))
    }
}
```

```kotlin
// 호스트 app/build.gradle.kts
dependencies {
    implementation("com.estaid:loginkit:2.0.1") // 위 substitution 으로 로컬 :estloginkit 로 치환됨
}
```

> 실제 적용 예 (Android-ZUM 레포에서): `includeBuild("../../ESTLoginKit/ESTLoginKit-Android")`.
> composite build 라 자체 카탈로그가 격리되고, SDK 수정이 즉시 반영됩니다. 안정화 후 Maven 아티팩트로 전환하세요.
> 카카오/jitpack 등 SDK 저장소는 이 레포의 `settings.gradle.kts` 가 자체적으로 선언하므로 호스트는 추가할 필요가 없습니다.

### 안정화 후 — Maven 아티팩트

```kotlin
// settings.gradle.kts repositories
maven {
  url = uri("https://maven.zuminternet.com/artifactory/libs-release-local")
  isAllowInsecureProtocol = true
}
maven("https://devrepo.kakao.com/nexus/content/groups/public/")

// app/build.gradle.kts
dependencies {
  implementation("com.estaid:loginkit:2.0.1")
}
```

배포: `./gradlew :estloginkit:publish`

## 2. 매니페스트 설정 (카카오 로그인 사용 시)

SDK 가 카카오 로그인 Activity 를 자동 등록합니다. 호스트는 카카오 scheme placeholder 만 설정합니다.

```kotlin
android {
  buildTypes {
    debug   { manifestPlaceholders["kakaoAuthScheme"] = "kakao{네이티브_앱_키}" }
    release { manifestPlaceholders["kakaoAuthScheme"] = "kakao{네이티브_앱_키}" }
  }
}
```

> `AuthCodeHandlerActivity` 나 `<queries><package android:name="com.kakao.talk" /></queries>` 를
> 호스트 매니페스트에 **직접 선언하지 마세요** — SDK 가 제공합니다.
> 카카오 미사용 시 `manifestPlaceholders["kakaoAuthScheme"] = ""`.

## 3. 초기화

```kotlin
class MyApp : Application() {
  override fun onCreate() {
    super.onCreate()
    EstLoginManager.initialize(
      context = this,
      config = EstLoginConfiguration.Builder(clientId = "발급받은_클라이언트_ID")
        .useEnvironment(EstEnvironment.PRODUCTION) // 기본값: PRODUCTION (개발: DEVELOPMENT)
        .useCallbackUrl("https://estoneid.com/auth/app-callback") // 미지정 시 환경 웹 baseUrl 로 자동 구성
        .useKakao(KakaoConfiguration(appKey = "발급받은_카카오_네이티브_앱_키"))
        .useNaver(
          NaverConfiguration(
            appName = getString(R.string.app_name),
            clientId = "발급받은_네이버_클라이언트_ID",
            clientSecret = "발급받은_네이버_클라이언트_시크릿",
          ),
        )
        .webViewInspectable(BuildConfig.DEBUG)
        .debugMode(BuildConfig.DEBUG)
        .build(),
    )
  }
}
```

### Builder 옵션

| 메서드 | 기본값 | 설명 |
|---|---|---|
| `Builder(clientId)` | (필수) | 클라이언트 ID |
| `useEnvironment(env)` | `EstEnvironment.PRODUCTION` | 웹·API base URL 을 환경이 쌍으로 결정(`loginUrl()`/`mypageUrl`/API host) |
| `useCallbackUrl(url)` | `{baseUrl}/auth/app-callback` | ssoToken(`code` 쿼리) 회수 지점. 로그인 URL 에 `state` 가 없으면 매칭 즉시 완료, 있으면 통과 후 `state` 착지 시 완료 (README §웹뷰 로그인 상세) |
| `useKakao(config)` | null | 카카오 설정 |
| `useNaver(config)` | null | 네이버 설정 |
| `useExtraUserAgent(s)` | null | SDK WebView UA 뒤 append |
| `webViewInspectable(b)` | false | Chrome DevTools inspect |
| `debugMode(b)` | false | SSL 우회 + 로깅 |
| `onWebViewCreated(block)` | null | WebView 생성 직후 1회 (Hackle 등 wiring) |
| `onPasswordChanged(block)` | null | 마이페이지 비번 변경 통지 |
| `onAccountDeleted(block)` | null | 마이페이지 회원 탈퇴 통지 |

## 4. JS 브릿지 프로토콜

웹 로그인 페이지가 구현해야 하는 인터페이스.

### 웹 → 네이티브

JS 브릿지 객체 이름은 `AndroidInterface` 입니다.

```javascript
AndroidInterface.requestSnsLogin(JSON.stringify({ type: "sns-login", provider: "kakao" }));
// provider: "kakao" | "naver" | "google" | "apple"
AndroidInterface.onLoginComplete(message);  // 로그인 완료 통지(관찰/로깅용). 실제 ssoToken 회수·dismiss 는 callbackUrl 매칭으로 처리됨
AndroidInterface.onPasswordChanged();       // 마이페이지 비밀번호 변경 통지
AndroidInterface.onAccountDeleted();        // 마이페이지 회원 탈퇴 통지
```

### 네이티브 → 웹

```javascript
window.onNativeSnsLoginResult = function (result) {
  // result.provider, result.authorizeToken, result.refreshToken, result.ci, result.email
};
window.onNativeSnsLoginError = function (error) {
  // error.code: "cancelled" | "sdk_error" | "unsupported_provider", error.message, error.provider
};
```

> 구글/애플은 네이티브 미지원 → `unsupported_provider` 를 반환합니다. 웹 OAuth 경로(`EstLoginManager.startWebLogin` / `EstLoginWebView`)를 사용하세요.

## 5. 쿠키 / 세션 관리 (호스트 책임)

- SDK 는 특정 도메인을 하드코딩하지 않습니다. Android `CookieManager` 는 프로세스 전역 공유이므로,
  WebView 로그인이 남긴 쿠키를 호스트 WebView 가 그대로 활용합니다.
- `EstLoginManager.logout()` 은 네이티브 토큰 + WebView 세션 데이터(쿠키/localStorage)를 정리합니다.
  서비스 도메인 고유의 세션 유지 정책(예: 정책 페이지 keepalive)이 필요하면 호스트에서 구현하세요.

## 6. 무결합(stateless) 설계 노트

이 SDK 는 특정 서비스(ZUM 등)에 결합되지 않은 stateless 설계입니다. 구버전 Android 모듈
(`com.estaid.auth`)에 있던 서비스 결합 요소는 모두 제거되었으며, 호스트 앱은 어떤 서비스에서도
동일하게 통합할 수 있습니다.

- 네트워크 호출은 표준 `Bearer` 토큰만 사용합니다. (`Accept: application/vnd.zum.resource-…` 같은 서비스 전용 헤더 없음)
- SDK 는 토큰을 보관하거나 서명하지 않습니다. (서비스 전용 JWT vendor/issuer 없음)
- WebView 의 구글 로그인 UA 처리는 일반 `accounts.google.com` 만 감지합니다. (서비스 전용 도메인 우회 없음)
- 로그아웃은 REST `checkLogin`/`logout` 호출 없이, 네이티브 SDK 토큰 + WebView 세션 데이터 정리로만 동작합니다.
