plugins {
    // includeBuild 로 물린 composite build 라, Android Studio "Clean Project" 가 이 루트에
    // clean 을 돌린다. base 플러그인이 루트에 lifecycle clean 태스크를 만든다.
    base
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
}
