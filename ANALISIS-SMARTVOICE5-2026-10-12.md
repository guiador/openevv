# Análisis de SmartVoice5 para OpenEVV

Fecha: 12 de octubre de 2026. Revisión estática del repositorio oficial, sin modificar el motor ni los instaladores de OpenEVV.

## Copia examinada y alcance

- Repositorio: https://github.com/poretsky/SmartVoice5/
- Commit: `e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127`, mensaje «Release 5.1.2», fechado el 12 de octubre de 2026.
- Copia de revisión: `build/smartvoice5-review/repository`.
- Se examinaron servicio TTS, selección y configuración de voces, reconocimiento de idiomas, integración de motores externos, adaptación de audio y puente nativo Pico. No se compilaron ni ejecutaron APK, ni se midió batería. Los submódulos ru_tts y CLDR no se descargaron recursivamente.
- La web consultada todavía presenta 5.1.0. Para determinar el estado actual del código se utilizó el commit anterior, no esa numeración de la web.

## Conclusión

Lo más aprovechable es la gestión de voces e idiomas en la aplicación. SmartVoice5 es un agregador: decide qué voz debe leer cada fragmento y puede delegar en otros sintetizadores instalados. Incluye Pico y un sintetizador ruso; no necesita eSpeak para ese diseño. Eso no convierte su detector en un motor fonético ni mejora directamente la pronunciación de OpenEVV.

La detección de japonés y polaco no equivale a tener voces nativas para esos idiomas. Según la documentación, las voces incorporadas cubren inglés, francés, alemán, italiano, ruso y español; para otros idiomas puede recurrir a motores externos. Incorporar la selección de idiomas no desbloquearía voces japonesas o polacas dentro de EVV.

## Qué conviene rescatar

| Idea | Utilidad para OpenEVV | Condiciones recomendadas |
|---|---|---|
| Configuración individual de velocidad, tono y volumen por voz | Alta: evita tener que reajustar todo al cambiar de voz | Guardar perfiles persistentes; distinguir valores guardados de cambios temporales para mayúsculas; ofrecer restablecimiento |
| Selector rápido con búsqueda y acceso a ajustes de la voz | Alta para Android; la búsqueda de voces también puede servir en NVDA | Mostrar claramente si la elección es permanente o temporal; no hacerla caducar silenciosamente |
| Asignación de una voz por idioma y orden de preferencia | Alta para lectura multilingüe opcional | Respetar la voz y el idioma pedidos por el lector; mantener la selección ante texto ambiguo |
| Segmentación de un mensaje por idioma o una sola voz para todo el mensaje | Media | Desactivada por defecto; limitarla a idiomas realmente disponibles; probar mensajes cortos y mezclas de números, nombres y abreviaturas |
| Voz específica para números y emojis | Media, opcional | No cambiar automáticamente la lectura de números ni duplicar la verbalización que ya realiza NVDA |
| Uso de otro motor instalado para idiomas ausentes | Posible en Android | Solo mediante elección explícita; cargar motores bajo demanda y prevenir ciclos entre agregadores |
| Adaptación de formatos de audio y cancelación | Referencia técnica | EVV ya dispone de su propia síntesis y conversión; no reemplazarlas sin una ventaja medida |

No recomiendo trasladar todo el servicio, incorporar Pico para sustituir EVV, importar bibliotecas Vocalizer ni copiar corpus lingüísticos sin revisar su procedencia. En NVDA, los cambios de idioma deben coordinarse con las órdenes de idioma y la verbalización del propio lector.

## La experiencia antigua: Español de España, voces cambiantes y batería

### Diferencia de generaciones

El historial oficial de la generación 4.x, disponible en el índice consultado de la página de novedades, describe soporte para Vocalizer Expressive v2 y abandono de v1, instalación mediante APK o carpeta de voces, permisos de acceso a archivos y soporte de 64 bits. Por tanto, el recuerdo de Vocalizer corresponde a una arquitectura anterior. SmartVoice5 actual incorpora Pico y el sintetizador ruso y agrega los TTS del dispositivo. Lo encontrado en 5.1.2 no demuestra cómo fallaba aquella versión.

### Tener que poner el dispositivo en Español de España

La explicación más plausible es un problema al resolver variantes regionales o inicializar una voz: una petición `es-PE` o `es-MX` no coincidía con una voz disponible `es-ES`, mientras cambiar la configuración del teléfono producía una coincidencia directa. Es una hipótesis, no un fallo histórico reproducido.

En el código actual, `PicoTtsEngine` enumera `spa-ESP` como español incorporado. Sin embargo, el puente nativo de Pico sí incluye una alternativa cuando encuentra el idioma pero no el país: conserva la coincidencia de idioma. Esto contradice la idea de que Pico necesite por diseño que todo el teléfono esté configurado en España. La resolución previa en el servicio y sus asignaciones de voces son otra capa que también debe funcionar correctamente.

Sin el APK concreto de aquella época y un registro del fallo no se puede distinguir entre resolución regional, estado de inicialización, acceso a archivos de voces o fallo dentro de la biblioteca nativa. No hay evidencia suficiente para culpar a reglas fonéticas del español.

Para EVV, la conducta deseable es resolver primero voz explícita, después variante regional compatible y finalmente español disponible. Si no existe una voz utilizable, debe devolver un error controlado y mostrar el motivo, sin cambiar los ajustes guardados ni exigir cambiar el idioma del dispositivo.

### Que eligiese voces aparentemente al azar

Hay mecanismos concretos en SmartVoice5 que pueden producir esa impresión:

- La detección utiliza alfabetos, expresiones, listas de palabras mediante filtros Bloom y un orden de prioridad. El texto corto puede ser ambiguo; ese orden influye en la decisión.
- Puede conservar el idioma anterior y preferir el idioma de la interfaz antes de recurrir a la petición del cliente.
- La elección en el selector rápido es temporal. En `SmartVoiceApp`, `explicitVoice` tiene un plazo de 60 000 ms; se renueva al terminar la síntesis y se limpia al refrescar la aplicación. La documentación también indica que se pierde al reiniciar, desactivar el control de notificación o pasar más de un minuto sin usar el sintetizador. Es distinto de guardar una asignación permanente por idioma.

Estos comportamientos están confirmados para la versión actual; no prueban que fueran la causa exacta de tu problema antiguo. Tampoco encontré fundamento para que fuese necesario «agarrarlo durante el reinicio» como procedimiento normal.

No conviene copiar esa caducidad invisible a EVV. Una selección manual persistente debe prevalecer hasta que el usuario la cambie. Si existe un modo temporal, debe identificarse como tal. Una opción útil sería explicar por qué se eligió otra voz: solicitud del lector, selección manual o detección automática.

### Consumo de batería

La notificación opcional utiliza un servicio en primer plano para favorecer que el sistema conserve el sintetizador. `TtsIntegrator.loadEngines()` prepara integraciones para todos los servicios TTS disponibles, excluyendo su propio paquete. También existe conversión de audio a la frecuencia nativa de salida y procesamiento por fragmentos.

Son puntos razonables para investigar memoria, arranque y CPU, pero no demuestran un consumo excesivo de batería. No encontré referencias a `WakeLock`, `PowerManager` o permiso `WAKE_LOCK` en `src/main`. Mantener un servicio residente no equivale por sí solo a mantener la CPU despierta. Harían falta mediciones en el dispositivo, incluyendo síntesis activa, reposo, motores externos y notificación activada/desactivada. No sería correcto atribuir el consumo histórico a Vocalizer o a la detección sin esas mediciones.

## Precauciones de implementación observadas

- `Criterion` convierte palabras con `Locale.getDefault()`. Para un detector lingüístico conviene una normalización controlada, independiente de la configuración regional del teléfono. No se ha demostrado que esto provoque el crash español.
- `ExpirableHint` utiliza hora de reloj (`System.currentTimeMillis()`). Para plazos internos, un reloj monotónico evita cambios por ajustes de hora.
- El integrador excluye directamente el propio paquete; una integración futura también debería impedir ciclos indirectos entre agregadores y cargar únicamente motores elegidos.
- `SoundFormatAdapter` fija salida mono PCM de 16 bits a la frecuencia nativa de Android, aceptando entradas entre 8 y 48 kHz. No aporta por sí mismo un nuevo modo nativo de síntesis a EVV ni justifica reemplazar el trabajo de 16 kHz ya realizado.
- El servicio comprueba que exista una voz antes de aplicar sus parámetros. La revisión no permite afirmar un fallo de puntero nulo simplemente porque no haya motores; esa ruta devuelve error cuando no obtiene voz.

## Licencias

La licencia raíz es MIT, copyright 2026 Igor B. Poretsky. No cubre de forma uniforme todos los componentes: Pico lleva encabezados Apache 2.0 y el remuestreador Java `libresample4j` declara LGPL. Los submódulos, datos de voces, corpus y recursos derivados de terceros necesitan una revisión específica antes de redistribuirlos. No debe asumirse que las antiguas bibliotecas Vocalizer se pueden reutilizar por estar asociadas a un proyecto abierto.

La vía más simple es implementar las ideas de interfaz y selección en el código propio de EVV. Si se reutiliza código concreto, conservar avisos y verificar la licencia de cada archivo y dependencia.

## Fuentes verificables

- [Código examinado, commit fijo](https://github.com/poretsky/SmartVoice5/tree/e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127).
- [Servicio y resolución de voces](https://github.com/poretsky/SmartVoice5/blob/e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127/src/main/java/tts/smartvoice/TtsService.java).
- [Selección temporal y motores incorporados](https://github.com/poretsky/SmartVoice5/blob/e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127/src/main/java/tts/smartvoice/SmartVoiceApp.java).
- [Voces de Pico](https://github.com/poretsky/SmartVoice5/blob/e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127/src/main/java/tts/synth/PicoTtsEngine.java).
- [Resolución regional nativa de Pico](https://github.com/poretsky/SmartVoice5/blob/e6c8ddd28bf318e3d97d1fe4a7eabf08f090b127/jni/pico/tts/com_svox_picottsengine.cpp).
- [Documentación oficial de selección y reconocimiento](https://poretsky.github.io/android/smartvoice/).
- [Novedades oficiales](https://poretsky.github.io/android/smartvoice/releasenotes.html): la página actual muestra 5.x; el índice consultado conserva contenido anterior 4.x sobre Vocalizer. No es una prueba de un crash histórico.

Resultado: análisis terminado; ninguna característica de este repositorio se ha incorporado todavía a EVV.

## Ampliación: recuento de voces y vestigios de Vocalizer

Se revisó además el historial completo disponible de este repositorio, desde su commit inicial hasta 5.1.2, así como el árbol de v5.0.0. No se encontraron adaptadores ni referencias de integración Vocalizer, VocalizerEx, Nuance o Expressive en el código, recursos, recetas nativas o bibliotecas actuales. Las coincidencias de «nuance» y «expressive» en corpus de investigación son palabras de diccionarios, no integraciones del sintetizador. No se encontró un historial de archivos con nombres Vocalizer/Nuance. Esto delimita lo observado en SmartVoice5; no demuestra que las versiones anteriores de SmartVoice careciesen de esas bibliotecas.

El recuento actual tiene tres niveles distintos:

- Pico anuncia seis entradas: inglés estadounidense y británico, alemán, español de España, francés e italiano.
- RussianVoiceEngine anuncia dos variantes, masculina y femenina, del mismo sintetizador ruso. Con Pico son ocho entradas incorporadas para seis idiomas distintos, siempre que ambos motores inicialicen correctamente.
- La detección y los diccionarios de emojis cubren una lista más extensa. Por separado, el catálogo puede ampliarse con las voces que anuncian otros TTS instalados. Ni el número de recursos lingüísticos ni el de idiomas detectables cuenta las voces incorporadas.

El relato del usuario sobre bibliotecas `.so` y datos Vocalizer adicionales se incorpora como contexto histórico. Una biblioteca nativa contiene el código que interpreta los recursos de voz; no equivale por sí sola a disponer de todas las voces. Empaquetar datos dentro de un APK puede requerir cambiar su contenedor, rutas, nombres, manifestaciones o extracción al almacenamiento. No permite concluir que se convirtiese el formato interno de los datos: para determinar esa diferencia haría falta examinar un paquete antiguo concreto. Tampoco una voz conocida por la interfaz garantiza que todos sus datos estén instalados o sean compatibles con la biblioteca v1/v2 utilizada.

Hallazgo adicional relevante: el commit [e701662](https://github.com/poretsky/SmartVoice5/commit/e701662d22d6be4f70f60c04c4782429385f0b7b), «Prevented exceptions on incorrect locale data of thirdparty voices», añadió protección frente a excepciones al obtener códigos ISO3 de idioma y país de voces externas. Confirma que los metadatos regionales han requerido correcciones reales en esta generación. No prueba que sea el mismo crash antiguo que desaparecía al elegir Español de España.
