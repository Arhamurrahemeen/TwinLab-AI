// Top-level build file — plugin versions declared here, applied in :app.
plugins {
    id("com.android.application") version "8.7.2" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    // google-services is added in Task 9 (needs android/app/google-services.json):
    // id("com.google.gms.google-services") version "4.4.2" apply false
}
