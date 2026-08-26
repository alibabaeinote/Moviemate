# Firestore/Firebase model classes are reflected over during (de)serialization.
-keepclassmembers class com.moviemate.app.data.model.** { *; }
-keep class com.moviemate.app.data.model.** { *; }

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
