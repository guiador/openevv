# Edición experimental en español y ecualización

Esta guía describe la primera edición. Para los APK Principal y Auxiliar de la versión 0.2, sus categorías y su política de actualizaciones, consulta [ARQUITECTURA-0.2.md](ARQUITECTURA-0.2.md).

La edición actual incorpora cinco idiomas de interfaz y preparación para Android 17.
Consulta [ANDROID17-IDIOMAS.md](ANDROID17-IDIOMAS.md) para los cambios y sus pruebas.

## Instalación y primera prueba

El APK de depuración contiene el motor para ARM de 32 bits (`armeabi-v7a`) y ARM de 64 bits (`arm64-v8a`). Requiere Android 6.0 o posterior. No incluye procesadores Intel. Está firmado para pruebas y usa el mismo identificador que el proyecto original; una instalación firmada con otra clave no permite actualizar directamente.

1. Instala el APK y abre la aplicación.
2. En **Ajustes de voz**, elige **español (España)**, **español (México)** u otra de las ocho variantes disponibles. La interfaz utiliza el mismo idioma que esta selección.
3. Pulsa **Reproducir muestra** para escuchar los cambios.
4. Para usarlo con TalkBack, selecciónalo en los ajustes de síntesis de voz de Android.
5. El **Generador de audio** permite escribir textos propios y guardar o compartir WAV.

## Controles añadidos

El ecualizador se encuentra en **Ecualizador y perfiles de timbre**, después de Calidad. Sus cinco bandas están centradas en 120, 350, 1000, 2500 y 3500 Hz. Cada banda y la preamplificación admiten valores enteros de −12 a +12 dB. Los botones −1 dB y +1 dB permiten ajustar sin arrastrar el deslizador.

| Perfil | 120 Hz | 350 Hz | 1000 Hz | 2500 Hz | 3500 Hz | Preamplificación |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Neutro | 0 | 0 | 0 | 0 | 0 | 0 |
| Profundo | +6 | +4 | 0 | −2 | −3 | −4 |
| Agudo | −3 | −2 | +1 | +4 | +5 | −4 |
| Suave | +2 | +1 | 0 | −4 | −6 | −2 |
| Claridad | −4 | −2 | +2 | +4 | +1 | −3 |

Modificar una banda muestra **Personalizado**. **Neutro** restablece solo el ecualizador. Los ajustes persisten al cerrar la aplicación y se aplican al servicio TTS, la muestra y la exportación WAV.

Los botones de timbre **Profunda**, **Aguda** y **Suave** modifican cuatro parámetros del motor en la voz seleccionada:

| Timbre | Tamaño de cabeza | Tono base | Aspereza | Soplosidad |
| --- | ---: | ---: | ---: | ---: |
| Profunda | 75 | 30 | 15 | 10 |
| Aguda | 30 | 75 | 5 | 10 |
| Suave | 50 | 50 | 0 | 25 |

Son valores nativos en la escala 0–100. La velocidad, el volumen, la variación tonal y el género vocal siguen siendo controles independientes. **Restablecer voz** restaura el timbre de la voz seleccionada; el ecualizador se restablece con **Neutro**.

El remuestreo expone las cuatro opciones implementadas por el motor: retención, interpolación lineal, interpolación cúbica y sinc. **Según calidad** utiliza la opción que ya elegía la optimización de audio. El audio de origen es de 11 025 Hz; seleccionar 48 000 Hz no genera detalle acústico que no estuviera en el original.

## Alcance lingüístico

La ecualización cambia el balance espectral y los perfiles modifican parámetros acústicos de la voz. Esta edición conserva las reglas Delta, los inventarios fonéticos y los diccionarios de cada dialecto. No constituye una nueva variante regional ni una corrección demostrada de la articulación de /s/. Permite comparar textos y timbres con el mismo motor antes de modificar las reglas.

La interfaz se tradujo en los recursos predeterminados para que aparezca en español también en teléfonos configurados en otros idiomas. Se conservaron las muestras habladas de cada idioma y los nombres propios de las voces. Los nombres de los idiomas se muestran en español.

## Comprobaciones realizadas

`tools/EqualizerCheck.kt` usa el mismo código Kotlin de producción. Comprueba neutralidad exacta del ecualizador, respuesta en ganancia de las cinco bandas en las siete frecuencias de muestreo, límites de salida de los tres modos con ganancia máxima, bytes incompletos, silencio, reinicio y elección del remuestreo. Estas comprobaciones se ejecutaron correctamente en el ordenador.

La ausencia de un dispositivo conectado impide confirmar escucha, navegación con TalkBack y estabilidad de síntesis en hardware ARM. Estas comprobaciones de procesamiento de audio no prueban la ausencia de cuelgues del motor nativo ni identifican textos que los desencadenen.

## Compilación

Con JDK 17 o 21, Android SDK 37.0, NDK 28.2.13676358, Bash, Make y Python disponibles:

```powershell
.\gradlew.bat --no-daemon :app:assembleDebug '-Pevvdroid.abis=arm64-v8a,armeabi-v7a'
```

El resultado se guarda en `app/build/outputs/apk/debug/app-debug.apk`. Las bibliotecas nativas sin eliminar símbolos quedan en `native/openevv/build/android-<abi>/libevvjni.unstripped.so` para localizar direcciones de fallos en registros de Android.
