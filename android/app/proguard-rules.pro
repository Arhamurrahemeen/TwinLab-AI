# Release build is not minified (see app/build.gradle.kts). Rules kept for later.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.omnitex.twinlab.**$$serializer { *; }
-keepclassmembers class com.omnitex.twinlab.** { *** Companion; }
-keepclasseswithmembers class com.omnitex.twinlab.** { kotlinx.serialization.KSerializer serializer(...); }
