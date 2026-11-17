# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers @kotlinx.serialization.Serializable class com.pfmap.app.** {
    *** Companion;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pfmap.app.**$$serializer { *; }
# MapLibre uses JNI + reflection
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**
