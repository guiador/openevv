# OpenEVV Español 0.2: navegación, dos motores y audio

## Dos instalaciones

| Aplicación | Identificador de Android | Uso sugerido |
| --- | --- | --- |
| OpenEVV Español Principal | `org.openevv.android.principal` | Motor predeterminado del sistema |
| OpenEVV Español Auxiliar | `org.openevv.android.auxiliar` | Motor elegido expresamente en una segunda aplicación |

Ambos APK contienen ARM de 32 y 64 bits y requieren Android 6.0 o posterior. Se pueden instalar a la vez y junto al puente anterior. Son instalaciones nuevas: Android no traslada los ajustes privados ni los diccionarios de la edición anterior. Puedes exportar e importar tus diccionarios.

Cada instalación tiene sus propias preferencias, diccionarios, servicio, proceso y biblioteca nativa. No se declaran dos servicios TTS con el mismo identificador: Android identifica un motor por su paquete y su API espera un único servicio TTS por paquete. El código de referencia de [TtsEngines](https://android.googlesource.com/platform/frameworks/base/+/d24f94989fe5e78b82af261727d3c2ba11639772/core/java/android/speech/tts/TtsEngines.java) documenta esa restricción. [TextToSpeechService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/core/java/android/speech/tts/TextToSpeechService.java) crea un hilo de síntesis y un controlador de reproducción para su servicio.

### Asignación

- Si el lector sigue el motor predeterminado, selecciona Principal en Android y Auxiliar dentro de la otra aplicación.
- Si el lector permite elegir motor, puedes asignarle Auxiliar y conservar Principal para las demás aplicaciones.
- Si ambos clientes solo usan el predeterminado, seguirán compartiendo servicio y cola. El motor no puede cambiar la selección de otra aplicación.

Separar colas de síntesis no crea dos altavoces ni garantiza reproducción simultánea: el foco de audio, la mezcla y la reducción de volumen dependen de Android y de las aplicaciones. Es necesario comprobarlo en un teléfono con el lector concreto. Los procesos separados evitan compartir las variables globales del motor nativo, incluida la elección de remuestreo.

## Navegación accesible

La pantalla de ajustes ofrece cuatro categorías. Solo se muestran las opciones de la subcategoría abierta.

- **Voz y sonido**
  - Idioma y selección de voz.
  - Características de la voz: velocidad y volumen; tono y entonación; resonancia y textura.
  - Calidad y reproducción.
  - Ecualizador y perfiles: bandas y ganancia; perfiles de timbre; método de remuestreo.
- **Lectura de textos**
  - Letras, estilos y emojis.
  - Números y códigos.
  - Pausas y símbolos.
  - Textos de programación.
- **Pronunciación y diccionarios**
  - Mis palabras y diccionarios importados.
  - Mis abreviaturas y expansión de abreviaturas del motor.
- **Funcionamiento en Android**
  - Batería y diagnóstico.
  - Dos motores independientes.

Cada panel tiene un título semántico para accesibilidad y encabezados navegables. Volver sube un nivel; en el menú inicial regresa a la pantalla principal o cierra los ajustes si se abrieron desde Android. Al volver de un diccionario se conserva la categoría. Cada cambio de panel empieza al principio del contenido y detiene la muestra que estuviera reproduciéndose.

Los interruptores exponen estado y acción mediante `toggleable`; el botón Volver conserva la acción de accesibilidad de un botón estándar. Los diálogos de opciones permiten desplazamiento. Los perfiles de timbre se muestran en botones verticales de ancho completo. Los ajustes del ecualizador tienen botones explícitos para aumentar o reducir un decibelio además del deslizador.

Estas mejoras están implementadas; no se ha realizado una sesión de uso con TalkBack en un dispositivo físico.

## Frecuencia y profundidad de bits

**Hertz y bits describen cosas diferentes.** La frecuencia indica cuántas muestras se producen por segundo. La profundidad de 16 bits indica la precisión de amplitud de cada muestra. Aumentar una no aumenta automáticamente la otra.

En la configuración habitual de esta extracción, el motor trabaja a 11 025 Hz y remuestrea las salidas superiores. `src/eci/api/eci_env.c`, funciones `ev_rateHz` y `ev_engineHz`, expresa esa decisión. La salida de 16 000 Hz es realmente un flujo a 16 000 muestras por segundo, pero no contiene detalle acústico nuevo. Tampoco lo añaden las salidas de 22 050, 24 000 o 32 000 Hz. El límite de Nyquist de una fuente a 11 025 Hz es 5 512,5 Hz; su banda útil puede ser menor por el filtrado.

**Sinc es el valor predeterminado de esta edición.** El conversor nativo usa un sinc con ventana Kaiser, conserva historia entre bloques y filtra las réplicas espectrales. Esto permite convertir frecuencias sin conservar los escalones de la retención de muestras. No recupera información que no existía en la señal de origen. Se conservaron los otros métodos para comparación y experimentación.

Se añadió 24 000 Hz a la selección de salida. Se envía al motor como frecuencia literal: los códigos nativos 0–6 mantienen sus valores originales. El estándar ya presente de esta familia es 22 050 Hz; 22 500 Hz sería una frecuencia distinta.

### Síntesis nativa a frecuencia mayor

No requiere necesariamente reescribir todo. `ev_engineHz` ya dispone de una vía experimental activada mediante `EVV_UPSAMPLE=none`, y el código Klatt define un rango hasta 44 100 Hz. Generar más muestras directamente tampoco garantiza más fidelidad: los límites y los datos que alimentan los resonadores siguen determinando el sonido. La documentación y los comentarios de esta rama describen diferencias espectrales al cambiar de régimen.

Esta extracción usa síntesis **formántica Klatt**. El inventario de voces concatenativas Torrent aparece en interfaces históricas, pero sus datos no están incluidos, según `src/eci/lang/eci_vcinfo.c`. Para modernizar esta síntesis hay que revisar:

1. Las tablas de coeficientes y la estabilidad de los resonadores a cada frecuencia.
2. Los límites de formantes, anchos de banda, ruido y preénfasis.
3. Las duraciones, el tono fundamental y las reglas que dependen de la frecuencia.
4. Las reglas acústicas del español y el ruido de las fricativas, con comparaciones auditivas y espectrales.
5. Continuidad entre bloques, saturación, consumo y estabilidad en ARM de 32 y 64 bits.

La vía nativa experimental no se activa en estos APK. Los cambios de interfaz y remuestreo no se presentan como una ampliación de la calidad nativa ni como una corrección de las reglas fonéticas.

## Identidad y futuras actualizaciones

Se cambió la identidad pública de Android y el nombre visible. Se conserva por ahora el espacio de nombres interno de Kotlin y JNI para no romper el enlace con la biblioteca C. Ese espacio de nombres no determina qué motor aparece en Android: lo determina el identificador de instalación.

Se mantiene el crédito a los autores del puente y la licencia original. OpenEVV continúa siendo una dependencia nativa independiente mediante `.gitmodules`, con origen `https://github.com/Mudb0y/openevv.git`.

El actualizador del puente quedó desactivado en la actividad, en la interfaz y en sus operaciones de consulta, descarga e instalación. Se retiraron los permisos de Internet e instalación y el proveedor del instalador. No se ha creado un servidor de actualizaciones ni se descargan bibliotecas ejecutables dentro del teléfono.

La revisión de OpenEVV usada en esta compilación es `520585138206f1692c619ae2b45856cbbe4255b3`, con el parche de selección de remuestreo del puente. El 6 de octubre de 2026, una consulta de solo lectura al origen encontró una revisión posterior (`c3a253fb8975b6cd7e46dac9e3ef3c1aa531bfa1`); no se incorporó durante este cambio de interfaz. Mantener la dependencia separada permite revisar y actualizar el motor sin reemplazar los cambios de Android.

Para distribuir mejoras del motor hay que revisar la nueva revisión, adaptar o retirar los parches necesarios, recompilar Principal y Auxiliar y mantener la misma clave de firma en cada identidad. La actualización automática del motor permanece desactivada. Los APK entregados están firmados para pruebas.

## Compilación

```powershell
.\gradlew.bat --no-daemon :app:assemblePrincipalDebug :app:assembleAuxiliarDebug '-Pevvdroid.abis=arm64-v8a,armeabi-v7a'
```

Las salidas son `app/build/outputs/apk/principal/debug/app-principal-debug.apk` y `app/build/outputs/apk/auxiliar/debug/app-auxiliar-debug.apk`.
