-keep class io.bearound.telemetry.** { public *; }

# firebase-messaging is compileOnly (optional) — ignore its absent classes in R8
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**