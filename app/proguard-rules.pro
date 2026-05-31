# Add project specific ProGuard rules here.
-keepattributes *Annotation*
-keep class com.llmbalance.app.data.** { *; }
-keep class com.llmbalance.app.ntfy.** { *; }

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
