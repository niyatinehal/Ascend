# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class app.levelup2027.** {
    *** Companion;
}
-keepclasseswithmembers class app.levelup2027.** {
    kotlinx.serialization.KSerializer serializer(...);
}
# Glance action callbacks are instantiated reflectively by class name
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }

# Anthropic Java SDK (own-key AI mode): its models are (de)serialised by Jackson via reflection,
# and Kotlin metadata is needed by jackson-module-kotlin.
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-dontwarn com.fasterxml.jackson.**
-dontwarn com.github.victools.**
-dontwarn io.swagger.**
-dontwarn org.apache.hc.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn java.beans.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
