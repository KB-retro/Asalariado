# Reglas de R8 para la versión que se instala.

# pdfbox (lectura de nóminas en PDF): carga fuentes y tablas por nombre, se conserva entero.
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
-dontwarn com.gemalto.jp2.JP2Decoder
-dontwarn com.gemalto.jp2.JP2Encoder
-dontwarn com.tom_roush.**

# El código de la propia app es pequeño: se conserva sin ofuscar para que los errores se entiendan.
-keep class com.asalariado.** { *; }
-keepattributes SourceFile,LineNumberTable
