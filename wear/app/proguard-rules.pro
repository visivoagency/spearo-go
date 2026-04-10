# Retrofit
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.spearotracker.spearogo.services.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
