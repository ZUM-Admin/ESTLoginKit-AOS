# ESTLoginKit (Android) — 연동 가이드

## 1. 의존성 추가

### 전환기 (현재) — 로컬 경로 / includeBuild

ZUM 앱 등 호스트 레포에서 이 레포를 형제 디렉터리로 두고 참조합니다.

```kotlin
// 호스트 settings.gradle.kts
include(":estloginkit")
project(":estloginkit").projectDir = file("../ESTLoginKit-Android/estloginkit")

dependencyResolutionManagement {
  repositories {
    maven("https://devrepo.kakao.com/nexus/content/groups/public/") // 카카오
    maven("https://jitpack.io")
  }
}
```

```kotlin
// 호스트 app/build.gradle.kts
dependencies {
  implementation(project(":estloginkit"))
}
```

> 전환기에는 호스트가 로컬 경로로 직접 참조하므로 SDK 수정이 즉시 반영됩니다. 안정화 후 Maven 아티팩트로 전환하세요.

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
  implementation("com.estaid:loginkit:2.0.0")
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
        .useBaseUrl("https://estoneid.com")
        .useCallbackUrl("https://estoneid.com/auth/app-callback") // 미지정 시 baseUrl 로 자동 구성
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
| `useBaseUrl(url)` | `https://estoneid.com` | 지정 시 `loginUrl()`/`mypageUrl`/AUTH_API 자동 구성 |
| `useCallbackUrl(url)` | `{baseUrl}/auth/app-callback` | WebView 로그인 완료 감지(prefix + `code` 쿼리) |
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

```javascript
AndroidInterface.requestSnsLogin(JSON.stringify({ type: "sns-login", provider: "kakao" }));
// provider: "kakao" | "naver" | "google" | "apple"
AndroidInterface.onPasswordChanged();
AndroidInterface.onAccountDeleted();
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

## 6. ZUM 결합 제거 노트 (iOS 정합)

iOS 원본과 달리 구버전 Android 모듈(`com.estaid.auth`)에 있던 ZUM 결합은 이 레포에서 제거되었습니다.

- ❌ `Accept: application/vnd.zum.resource-…` 헤더 → 제거 (stateless, Bearer 만 사용)
- ❌ JWT 서명(`jwtVendor/Issuer="ZUM"`) → 제거 (토큰 비보관)
- ❌ WebView Google UA 우회의 `sign.zum.com` 도메인 → 일반 `accounts.google.com` 만 감지
- ❌ REST `checkLogin`/`logout` → 제거 (로그아웃은 네이티브 토큰 + 세션 정리)
