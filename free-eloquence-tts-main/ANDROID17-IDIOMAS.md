# Android 17 e idiomas

La compilación utiliza Android API 37.0, AGP 9.1.1, Gradle 9.3.1 y NDK r28c
(28.2.13676358). Kotlin integrado y el compilador Compose usan 2.2.21.
Se mantienen las bibliotecas de interfaz existentes para conservar Android 6
(minSdk 23), en lugar de adoptar versiones que requieren Android 7.

`targetSdk` permanece en 36. Compilar con API 37 permite preparar y comprobar
la aplicación para Android 17 sin activar todavía las restricciones adicionales
de audio en segundo plano de las aplicaciones que apuntan a API 37.
Antes de elevarlo, hay que probar los servicios con permisos de uso en primer
plano, el inicio después del reinicio y la lectura con TalkBack, pantalla apagada
y auriculares Bluetooth. Referencias:
[configuración del SDK](https://developer.android.com/about/versions/17/setup-sdk),
[compatibilidad de AGP](https://developer.android.com/build/releases/agp-9-1-0-release-notes),
[audio en segundo plano](https://developer.android.com/about/versions/17/changes/bg-audio).

En Android 17, los canales de reproducción propia del motor requieren que el
servicio de notificación esté activo. Si no lo está, se entrega el audio a la
ruta estándar del sistema TTS con el canal solicitado por la aplicación.
Esta ruta también está sujeta a las restricciones de audio del sistema; no
garantiza por sí sola la reproducción en segundo plano. Para la lectura con
pantalla apagada, activa la notificación que mantiene el motor en primer plano
y comprueba el comportamiento en el dispositivo.
La reproducción de archivos en el generador de audio se pausa al salir de la
aplicación en Android 17 si no hay servicio en primer plano; conserva la posición
para reanudarla. El ruido Bluetooth tampoco se inicia sin una concesión real del
servicio en primer plano.
Los servicios de Android todavía pueden rechazar un inicio en segundo plano;
es necesaria una comprobación en un dispositivo real para confirmar esos casos.

La interfaz sigue el idioma de voz elegido en los ajustes: en-US, en-GB, es-ES,
es-MX, fr-FR, fr-CA, de-DE o it-IT. Las variantes regionales conservan su acento
en el motor y comparten las traducciones de su idioma en la interfaz. Una
solicitud de otro idioma desde un lector de pantalla no cambia la interfaz.
Las notificaciones y sus controles también usan la selección del motor.
El texto de muestra del sistema respeta el idioma solicitado o, si falta, el
idioma elegido. Los textos de prueba de cada motor permanecen en su idioma.

Las traducciones completas están en `app/src/main/res/values-*`. El inglés es
el idioma de reserva. El español es la fuente editorial y
`tools/translations.tsv` contiene inglés, alemán, francés e italiano.
Para regenerar y verificar:

```sh
python tools/generate_localizations.py
python tools/check_localizations.py
./gradlew :app:assemblePrincipalDebug :app:assembleAuxiliarDebug :app:compilePrincipalDebugAndroidTestKotlin
```

La comprobación verifica cobertura, plurales, codificación y argumentos de
formato. `SpeechLocaleTest` comprueba los ocho dialectos y sus traducciones en
Android; se ejecuta mediante `connectedPrincipalDebugAndroidTest` con un
dispositivo o emulador conectado.

## Comprobación realizada

Se compilaron y empaquetaron Principal y Auxiliar en depuración con API 37.0,
y se compiló el conjunto de pruebas instrumentadas, incluido `SpeechLocaleTest`.
Se verificaron los 546 recursos traducibles, sus plurales y argumentos; la
regeneración produce archivos idénticos. Las bibliotecas ARM existentes y su
empaquetado ZIP cumplen la alineación de 16 KB.

La comprobación de Gradle usó `-x buildNative`. Después se recompilaron las
bibliotecas ARM con el NDK 26.1 instalado localmente para incluir el arreglo
del remuestreo, y se volvieron a empaquetar ambas aplicaciones. Falta validar
la compilación nativa con el NDK r28c fijado en Gradle y la síntesis en Android
17. No se ejecutaron las pruebas instrumentadas porque no hay un dispositivo
o emulador conectado. No se validó un binario nativo x86_64.
