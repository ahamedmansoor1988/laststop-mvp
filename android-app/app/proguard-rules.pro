# Keep the app's data models intact — they are round-tripped through SharedPreferences and
# JSON by name, so renaming their fields would silently break saved state on upgrade.
-keep class com.laststop.app.SignedInUser { *; }
-keep class com.laststop.app.UserProfile { *; }
-keep class com.laststop.app.Destination { *; }

# OkHttp ships with these optional dependencies absent at runtime.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
