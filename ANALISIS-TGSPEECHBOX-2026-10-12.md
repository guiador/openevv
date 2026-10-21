# Análisis de TGSpeechBox para OpenEVV

Revisión del 12 de octubre de 2026, sobre `097739c5309e449d7efe04b6369690f58e71b93c`
(commit del 2 de abril de 2026). Se descargó una copia aislada en
`build/tgspeechbox-review/repository`. Análisis estático de documentación y
código; no se compiló TGSpeechBox ni se evaluaron voces o latencia audible.
No se incorporó código al motor ni se modificaron los instaladores actuales.

## Conclusión

Sí hay trabajo aprovechable sin eSpeak NG. Lo más valioso para nuestra edición
es la gestión de ajustes y diccionarios, seguida de algunas ideas de salida
de audio. Cambiar el generador de voz sería un proyecto experimental aparte.

TGSpeechBox separa texto → IPA (eSpeak), IPA → trayectorias temporales
(frontend propio y paquetes YAML) y trayectorias → PCM (DSP propio).
El DSP no necesita eSpeak para generar audio, pero tampoco recibe texto ni
PCM de otro sintetizador como sustituto de esas trayectorias.
Nuestra interfaz ECI entrega texto, controles y audio; no es equivalente a
`speechPlayer_queueFrameEx`. Un puente acústico exigiría trabajar dentro del
sintetizador Klatt de EVV y traducir frecuencias, amplitudes, tiempos e índices.

Fuentes: [arquitectura](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/Developers.md),
[API DSP](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/speechPlayer.h),
[frames](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/frame.h).

## Sobre las reglas inglesas

La revisión actual sí contiene ajustes españoles, vocales y consonantes
específicas y cambios de articulación. `packs/lang/es.yaml` desactiva varias
transformaciones generales y declara vocales sin diptongación inglesa.
Por tanto, no se confirma que todas sus reglas sean inglesas para todos
los idiomas. Eso no demuestra que su pronunciación española sea satisfactoria.

También hay herencia de `default.yaml`, una tabla común de fonemas y la
selección `legacyPitchMode: espeak_style` en español. La dependencia del
fonemizador aparece directamente en el controlador NVDA y el runtime SAPI.
La experiencia de incompatibilidad descrita por el usuario es compatible
con problemas en esa combinación, pero este análisis no identifica por oído
su causa concreta. La propuesta excluye fonemizador, reglas, diccionarios IPA,
tablas fonéticas e intonación de esa capa.

Fuentes: [español](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/packs/lang/es.yaml),
[herencia](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/frontend/pack.cpp),
[controlador](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/nvdaAddon/synthDrivers/tgSpeechBox/__init__.py).

## Qué merece incorporarse

| Componente | Valor para EVV | Adaptación necesaria | Prioridad |
|---|---|---|---|
| Perfiles de voz exportables/importables | Llevar timbre, tono, EQ y calidad entre Android y NVDA | Formato propio versionado, rangos validados, correspondencia de voz/idioma y restablecimiento seguro; no importar sus YAML acústicos | Alta |
| Indicar qué ajustes están personalizados | Distinguir el valor original del cambiado y recuperar un solo control | Comparar con los valores de la voz seleccionada; conservar nuestros resets por voz/EQ/modo | Alta |
| Prueba de pronunciación por entrada | Escuchar original y sustitución antes de guardar un diccionario | Motor aislado, controles de detener y cancelación; utilizar texto o formatos nativos EVV, no IPA TGSpeechBox | Alta |
| Editor de diccionarios más completo en NVDA | Buscar, editar y organizar entradas desde el complemento | Mantener los diccionarios NVDA y nativos existentes; distinguir idioma, origen y prioridad | Alta |
| Importación compartida de sustituciones | Compartir las mismas correcciones entre los dos paquetes | Mapear solamente columnas de texto compatibles, con vista previa y detección de duplicados; rechazar IPA y campos desconocidos | Media |
| Reacción al cambio de dispositivo de audio | Recuperación ante dispositivos nuevos o fallos de reproducción | Mantener el reproductor anterior hasta que el nuevo funcione y reintentar de forma acotada | Media |
| Control de picos con anticipación | Posible reducción de distorsión y bombeo al subir ganancia/EQ | Módulo optativo sobre PCM, escalas propias EVV, compensación de índices y coste/latencia medidos | Experimental |
| Transiciones al cancelar/reanudar | Posible reducción de clics de salida | Aplicar solo en fronteras reales; medir si se pierden consonantes o aumenta el tiempo de respuesta | Experimental |
| Envoltorio SAPI y editor Win32 | Referencia para una futura voz de sistema Windows | Sustituir todo el runtime eSpeak/frontend por ECI; arquitectura, registro e instalador nuevos | Futuro, fuera de los dos paquetes actuales |
| Generador LF, resonadores y acoplamiento fuente/tracto | Otro carácter de voz manteniendo el procesamiento lingüístico EVV | Puente de frames, calibración, aislamiento por instancia y regresiones en todas las arquitecturas | Coste alto; no integración directa |

Android EVV ya tiene búsqueda, importación y exportación de diccionarios.
Ambos paquetes ya tienen presets, resets, EQ y prueba de voz; NVDA también
exporta WAV. La propuesta mejora esos flujos donde falta algo, sin duplicarlos.

Referencias de implementación:
[panel NVDA](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/nvdaAddon/synthDrivers/tgSpeechBox/langPackSettingsPanel.py),
[editor de diccionarios](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/platforms/android/app/src/main/kotlin/com/tgspeechbox/tts/DictionaryEditorScreen.kt),
[editor de ajustes](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/platforms/android/app/src/main/kotlin/com/tgspeechbox/tts/EditorScreen.kt).

## Precauciones concretas del código

1. El panel NVDA acumula cambios normales para Aplicar y los borra al Cancelar,
   pero importar perfiles copia inmediatamente un archivo sobre `phonemes.yaml`
   después de una confirmación. Cancelar el panel no deshace esa copia. Para
   EVV conviene validar y mostrar los cambios antes de aplicarlos, con respaldo
   y escritura atómica; no sustituir el archivo completo de configuración.
2. El hilo de audio guarda el identificador del dispositivo nuevo antes de
   crear su reproductor. Si falla la creación, conserva el reproductor anterior,
   pero el identificador queda como si el cambio hubiera funcionado. En esa
   ruta, el siguiente despertar puede no reintentar el mismo cambio. No copiar
   esa secuencia sin corregirla. Es una conclusión de lectura, no una falla
   reproducida en un dispositivo.
3. El limitador introduce una ventana de anticipación nominal de 5 ms. La
   entrada de audio añade una prefijación silenciosa de 3 ms y un fundido de
   unos 12 ms. Estos números no son la latencia completa del sistema. Pueden
   suavizar cortes, pero también retrasar o atenuar ataques de consonantes.
   La voz original debería seguir disponible sin estos efectos.
4. El aumento de velocidad consume ticks extra del administrador de frames.
   Está ligado a ese generador: no es una rutina general para acelerar el PCM
   de EVV conservando tono e inteligibilidad.

Fuentes: [audio NVDA](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/nvdaAddon/synthDrivers/tgSpeechBox/audio.py),
[DSP y limitador](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/speechWaveGenerator.cpp).

## Licencias y procedencia

La licencia raíz y las cabeceras del DSP y del código SAPI declaran MIT,
con avisos de NV Access y Tamas Geczy. Los avisos deben conservarse al reutilizar
fragmentos cubiertos por esa licencia.

Sin embargo, `EditorScreen.kt`, `DictionaryEditorScreen.kt` y
`TgsbViewModel.kt` declaran expresamente **GPL-3.0**. Excluir eSpeak NG no
cambia esas cabeceras. No deben tratarse como archivos MIT basándose solo
en el resumen del README. Para nuestra interfaz recomiendo implementar las
ideas con código propio y revisar individualmente cualquier fragmento que
se quiera trasladar. También quedan fuera los datos fonéticos cuyo README
reconoce relación histórica con tablas eSpeak.

Fuentes: [licencia raíz](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/LICENSE),
[cabecera Android](https://github.com/tgeczy/TGSpeechBox/blob/097739c5309e449d7efe04b6369690f58e71b93c/src/platforms/android/app/src/main/kotlin/com/tgspeechbox/tts/EditorScreen.kt).

## Orden recomendado

Primero: perfiles portables y ajustes personalizados visibles. Segundo:
prueba de cada entrada y editor de diccionarios NVDA, utilizando EVV como
único motor lingüístico. Tercero: evaluar efectos de salida con comparaciones
A/B, distintos niveles de ganancia, frecuencias, velocidades e índices NVDA.

No hay evidencia de esta revisión que permita prometer más naturalidad,
menor latencia o mejor inteligibilidad al copiar su DSP. La evaluación acústica
queda pendiente y el generador LF merece una investigación separada, no una
sustitución del sonido clásico durante una mejora de interfaz.
