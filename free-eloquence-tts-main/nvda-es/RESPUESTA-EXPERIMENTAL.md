# Respuesta experimental en NVDA 2026.3 x64

Versión local 0.4.1. En **Ajustes de NVDA → OpenEVV → Respuesta experimental**.
Desactivado por defecto. Selecciona un perfil, aplica o guarda y prueba con
navegación rápida, escritura, lectura continua y cambios de idioma. El botón
«Restaurar y desactivar» recupera el comportamiento de 0.4.0 sin cambiar voz,
velocidad, ecualizador ni remuestreo. Conserva el instalador anterior para volver.

| Perfil | Caracteres orientativos | Bloque nativo | Umbral de entrega | Corte dentro de palabras |
|---|---:|---:|---:|---|
| Referencia | 500 | 2048 | 2048 | No |
| Equilibrado | 250 | 1024 | 1024 | No |
| Rápido | 100 | 512 | 512 | No |
| Extremo | 32 | 128 | 128 | Sí |

Cada control incluye explicación visible, nombre accesible y ayuda con ventajas
y fallos posibles. Los campos numéricos aceptan teclado. Los perfiles mantienen
la salida ligera desactivada; se prueba por separado para aislar su efecto.

**Fragmentos:** sin corte estricto se termina en espacios; una palabra extensa
puede superar el límite. El corte estricto puede alterar palabras, códigos,
caracteres combinados y prosodia. No detecta sílabas. No se aplica en ámbitos de
deletreo, prosodia temporal ni texto con anotaciones del motor. En estos casos
la unidad puede ser mayor para conservar el estado de la voz.

**Audio:** el bloque se configura entre llamadas de síntesis en el único hilo
que posee ECI. El umbral acumula PCM mono; puede excederse en un bloque, y los
índices de NVDA fuerzan la entrega antes de llegar al umbral. Las marcas siguen
ligadas al final del audio correspondiente. Solo la última unidad anuncia fin.

**Salida ligera:** omite cálculo de ganancia automática y saturación suave;
mantiene filtro DC, ecualizador, preamplificación y balance. Puede bajar el volumen
o distorsionar picos por recorte. Desactivarla recupera el procesamiento habitual.

El tiempo teórico de un bloque es `1000 × muestras / frecuencia` ms. A 11025 Hz,
2048 muestras representan 185,8 ms; 512, 46,4 ms; 128, 11,6 ms. Estos valores son
duraciones PCM y no mediciones del tiempo hasta oír voz. Los callbacks parciales,
marcas, carga del equipo y cola WASAPI alteran el resultado efectivo.

Los controles Android de AudioTrack, búfer circular JNI, anticipación temporal,
umbral de lectura en streaming y trabajadores de preparación no se copian: esta
implementación usa WavePlayer/WASAPI y prepara la secuencia de NVDA por otra ruta.
Añadir hilos a ECI no es seguro. La cancelación detiene el reproductor, descarta
audio y unidades pendientes; el motor todavía termina la unidad ya iniciada.

Validación: 54 síntesis con DLL Windows real en 18 combinaciones, cancelación y
recuperación, índices/final de habla, restauración de valores, conservación del
texto, protección de anotaciones/deletreo; panel y persistencia simulados.
Además: sintaxis, ZIP, arquitectura PE x64, exportaciones y reproducción exacta
del generador `prepare.py`. WavePlayer se simuló: queda pendiente la prueba
auditiva dentro de NVDA 2026.3, que sigue en fase beta a esta fecha. No se ha
instalado el complemento ni reiniciado el lector en ejecución.
