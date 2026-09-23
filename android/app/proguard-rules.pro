# --- kotlinx.serialization (официальные правила) ---
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class ru.astrosmap.app.**$$serializer { *; }
-keepclassmembers class ru.astrosmap.app.** { *** Companion; }
-keepclasseswithmembers class ru.astrosmap.app.** { kotlinx.serialization.KSerializer serializer(...); }

# --- Retrofit: дженерики сигнатур и suspend-обвязка ---
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * { @retrofit2.http.* <methods>; }
-dontwarn retrofit2.KotlinExtensions*
-dontwarn okhttp3.internal.platform.**
-dontwarn org.codehaus.mojo.animal_sniffer.*
-dontwarn javax.annotation.**
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
