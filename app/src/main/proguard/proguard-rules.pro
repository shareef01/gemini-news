# Add project specific R8 rules here.
# AGP will combine all keep rule files in src/main/proguard to pass to R8
#
# For more details, see
#   https://d.android.com/r/tools/r8/keep-rules

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# kotlinx.serialization (NewsAPI response models)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.aus.gemini01.**$$serializer { *; }
-keepclassmembers class com.aus.gemini01.** {
    *** Companion;
}
-keepclasseswithmembers class com.aus.gemini01.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit (generic signatures used reflectively)
-keepattributes Signature, Exceptions, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Article/Source are java.io.Serializable and used as rememberSaveable /
# adaptive-navigator keys. Field renames break Bundle restore after process death.
-keepnames class com.aus.gemini01.data.Article
-keepclassmembers class com.aus.gemini01.data.Article { <fields>; }
-keepnames class com.aus.gemini01.data.Source
-keepclassmembers class com.aus.gemini01.data.Source { <fields>; }
