# JNA finds native methods and structures by name, so R8 must not rename them.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class com.ascon.engine.adblock.rust.** { *; }
-dontwarn java.awt.**
