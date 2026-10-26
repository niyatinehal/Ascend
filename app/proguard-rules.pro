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
