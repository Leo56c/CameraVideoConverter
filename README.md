# Camera Video Converter

Aplicación Android para convertir videos de cámaras de doble lente y guardar:
- el video completo,
- solo la mitad superior,
- solo la mitad inferior.

La opción predeterminada es **lente inferior**. Los archivos se procesan localmente en el teléfono; la app no sube videos a Internet.

## Crear el APK

1. Instalar Android Studio en una computadora.
2. Abrir la carpeta `CameraVideoConverter` desde Android Studio.
3. Esperar a que Gradle descargue las dependencias.
4. Elegir **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. El APK de prueba se genera en `app/build/outputs/apk/debug/app-debug.apk`.

Requisitos de compilación: Android SDK 35 y JDK 17. La app admite Android 7.0 (API 24) o posterior.

## Nota
La app intenta codificar H.264 por hardware y, si el teléfono no lo admite, usa MPEG-4 como alternativa. La salida se guarda en la ubicación que el usuario elija mediante el selector de documentos de Android.
