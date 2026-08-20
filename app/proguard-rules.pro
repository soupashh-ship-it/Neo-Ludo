# NeoLudo ProGuard Rules
-keepattributes *Annotation*
-keepclassmembers class * {
    @androidx.room.* <methods>;
}
-keep class com.neoludo.game.engine.model.** { *; }
-keep class com.neoludo.game.multiplayer.model.** { *; }
