# Keep JavaScript interface methods
-keepclassmembers class com.lmarena.app.LMArenaWebBridge {
    @android.webkit.JavascriptInterface <methods>;
}
-keepattributes JavascriptInterface

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
