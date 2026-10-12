# Pruebas de respuesta con lectores de pantalla

La sección está en **Ajustes de voz → Funcionamiento en Android → Respuesta experimental**. Está desactivada por defecto. Se guardan los controles en una única preferencia y se toma una configuración por petición. El botón de restablecimiento desactiva los cambios y recupera la ruta normal. Cambiar un control afecta a la siguiente petición, no a los filtros de un bloque que ya se está procesando.

## Perfiles de partida

Son puntos de partida para comparar, no resultados medidos ni perfiles calibrados para todos los teléfonos. Elegir un perfil no activa el interruptor experimental ni cambia la voz, frecuencia de salida, remuestreador, ecualizador o ruta de reproducción.

| Parámetro | Referencia | Equilibrado | Rápido | Extremo |
|---|---:|---:|---:|---:|
| Fragmento, caracteres UTF-16 | 250 | 250 | 100 | 32 |
| Audio adelantado, ms | 150 | 120 | 60 | 20 |
| Bloque nativo, muestras | 2048 | 1024 | 512 | 128 |
| Bloque máximo de entrega, muestras | Automático | 1024 | 512 | 128 |
| Cola nativa activa, KiB | 256 | 64 | 32 | 16 |
| Preparación por fragmentos por encima de, caracteres | 10000 | 1000 | 0 | 0 |
| Fragmentos preparados en cola | 1 | 2 | 2 | 1 |
| Hilos de preparación | 1 | 1 | 2 | 2 |
| Búfer de reproducción propia solicitado, ms | 333 | 120 | 60 | 30 |
| Umbral de arranque, % del búfer efectivo | 100 | 50 | 25 | 10 |
| Permitir cortes dentro de palabras | No | No | No | Sí |

Todos dejan desactivado el procesamiento de salida ligero. El perfil Referencia conserva los tamaños anteriores, pero con el interruptor activo utiliza la preparación experimental. Para comparar con el comportamiento original, desactivar o restablecer.

## Qué significan los cálculos

Duración de bloque = muestras × 1000 / frecuencia de salida. Frecuencia teórica de llamadas = frecuencia de salida / muestras. Capacidad de cola en ms = KiB × 1024 × 1000 / (frecuencia × 2), porque el PCM es mono de 16 bits.

| Bloque | Audio a 11.025 Hz | Audio a 48.000 Hz |
|---|---:|---:|
| 128 muestras | 11,61 ms | 2,67 ms |
| 512 muestras | 46,44 ms | 10,67 ms |
| 1024 muestras | 92,88 ms | 21,33 ms |
| 2048 muestras | 185,76 ms | 42,67 ms |

Estas duraciones no son tiempos de cálculo ni latencias audibles. Los bloques parciales pueden ser más pequeños. Una cola grande no obliga a esperar a llenarla. El adelanto se comprueba después de cada entrega, así que puede excederse por un bloque; con los experimentos activos el reloj de adelanto comienza con la primera entrega, para no sumar la espera de preparación al audio permitido.

## Compensaciones y límites

- Los fragmentos pequeños permiten descartar antes el trabajo de una petición interrumpida, pero añaden límites prosódicos, llamadas y posibles micropausas. Conservar palabras completas no impone un máximo estricto a palabras largas.
- El aborto impide seguir entregando audio, pero el motor termina el fragmento en curso antes de admitir el siguiente. En reproducción automática, el lector y Android deciden cómo descartar el audio que ya recibieron; la aplicación solo puede vaciar directamente su reproductor propio.
- Los cortes agresivos se hacen en el texto original, antes de generar anotaciones del motor, y conservan los pares Unicode sustitutos. Pueden partir palabras, etiquetas SSML, direcciones, secuencias de emojis, coincidencias de diccionario y códigos. No son una detección de sílabas.
- El primer fragmento experimental se transforma y publica sin transformar antes el siguiente. En la preparación por fragmentos las reglas disponen de menos contexto.
- La cola de preparación contiene texto, no audio audible. Más profundidad puede suavizar retrasos, pero desperdicia trabajo al interrumpir.
- Los hilos adicionales permiten que una nueva petición prepare texto mientras una etapa cancelada termina. No sintetizan audio en paralelo ni paralelizan una transformación. Se comprueba la cancelación entre etapas; una etapa larga aún puede tardar en salir.
- El bloque nativo determina la granularidad de la salida del motor. El bloque de entrega limita lo que se lee de la cola por llamada JNI. Reducir solo el segundo no evita esperar a que se produzca el primero.
- La capacidad activa de la cola nativa cambia solo con el trabajador inactivo. La memoria máxima sigue reservada para reutilización, y un bloque siempre cabe en la cola. Se conservan los tiempos de seguridad del motor y el aborto mediante callback; no se introduce `eciStop`.
- El procesamiento ligero mantiene el filtro de continua y el ecualizador, omite el análisis de picos y la ganancia automática y sustituye `tanh` por recorte duro. Puede cambiar el volumen y producir distorsión con ganancias altas.
- El búfer de reproducción y el umbral solo afectan a la reproducción propia. La ruta automática depende de Android. El búfer efectivo respeta el mínimo del dispositivo; el umbral configurable requiere Android 12 o posterior. No se cambia la ruta automáticamente.

## Protocolo en el teléfono

1. Restablecer y escuchar el pasaje de prueba. Navegar también por una lista real con TalkBack.
2. Mantener iguales voz, velocidad, frecuencia, remuestreador, ecualizador y ruta.
3. Activar el perfil equilibrado; comparar después con el rápido. Cambiar un control cada vez.
4. La prueba de interrupciones utiliza el servicio TTS instalado y `QUEUE_FLUSH` para seis peticiones cada 350 ms. No reproduce gestos reales de TalkBack. Observar si el siguiente elemento arranca antes y si desaparecen partes que todavía se necesitaban.
5. Probar lectura continua, pantalla bloqueada y las salidas que se usen habitualmente. Valorar también batería, temperatura y micropausas.
6. Utilizar el perfil extremo solo para explorar el límite. Restablecer permite regresar a la configuración normal sin tocar la voz.

Las mediciones muestran texto preparado, primer PCM y primera entrega desde la entrada de la petición. No miden el altavoz, Bluetooth ni el momento de silencio realmente oído. La última petición puede pertenecer a TalkBack leyendo la propia pantalla de controles. La información del reproductor describe el último reproductor propio abierto y puede pertenecer a una petición anterior si Android tomó la ruta automática.

## Validación de esta implementación

Se recompila el puente JNI para ARM64 y ARM de 32 bits. El motor nativo no se modifica: se enlazan sus objetos existentes con el puente nuevo. La compilación normal del proyecto sigue disponible; `evvdroid.ndkVersion` permite seleccionar un NDK instalado sin cambiar el valor predeterminado.

Las comprobaciones de desarrollo cubren la persistencia y normalización de los perfiles, la recuperación de los valores normales al desactivar, la conservación del texto y de pares Unicode en ambos modos y los tamaños de salida con síntesis e interrupciones repetidas en el motor de escritorio. Esto no sustituye las pruebas de sonido ni accesibilidad en un dispositivo Android.
