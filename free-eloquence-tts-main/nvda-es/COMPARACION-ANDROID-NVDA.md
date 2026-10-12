# Comparación Android y complemento NVDA — 7 de octubre de 2026

Se comparó el código Android actual con el instalador real del 6 de octubre,
`Instaladores/openevv-es-0.3.0-nvda2026.3-x64.nvda-addon`, y con el código de
`nvda-es`. Esta carpeta descargada no conserva un historial Git completo de
las modificaciones Android; la comparación se basa en esos archivos y en
`AUDIO-CONTROLES.md` y `ANDROID17-IDIOMAS.md`, sin atribuir cambios a commits
históricos inexistentes.

| Función | Complemento de ayer, 0.3.0 | Complemento 0.4.0 |
| --- | --- | --- |
| Ocho módulos de idioma, ocho voces por módulo | Incluido | Conservado y probado con las 64 combinaciones |
| Velocidad, aceleración, tono, entonación, volumen | Incluido | Conservado en la categoría Voz de NVDA |
| Tipo de voz, resonancia, aspereza, aire y perfiles de timbre | Incluido | Conservado; etiquetas compartidas con Android |
| Pausas normales, acortar al final, acortar todas | Faltaba | Añadido; acortar todas es el valor inicial |
| Pausas explícitas de NVDA | Sin ajuste independiente | Control de 0–100 % para `BreakCommand` |
| Predicción de límites de frases | Faltaba | Añadida mediante `pp0`/`pp1`, independiente de las pausas |
| Forzar idioma, velocidad, tono y volumen | Faltaba | Añadido; ignora cambios solicitados por el documento y mantiene los ajustes del usuario |
| Números: normales, dígitos, pares, tríos | Faltaba | Añadido para cifras de seis o más dígitos; conserva decimales y separadores de miles |
| Códigos de verificación | Faltaba | Detección contextual en cinco idiomas y lectura por dígitos; adaptación conservadora, no réplica exacta de la puntuación de confianza Android |
| Deletreo de letras/radiofónico y palabras en mayúsculas | Faltaba | Cinco modos; respeta el deletreo que ya ordena NVDA |
| Mayúsculas aisladas | Sólo opciones propias de NVDA | Modos del motor añadidos; el deletreo por caracteres sigue respetando NVDA |
| Eliminar SSML, limpiar Unicode y corregir texto | Normalización básica | Ampliado; no elimina comparaciones literales como `a < b > c` |
| Lectura de código | Faltaba | Activación y siete opciones: camelCase, snake_case, operadores, delimitadores, sangría, rutas, URL |
| Diccionarios y abreviaturas personales | Diccionarios de NVDA | Integrados con los diccionarios de voz de NVDA; no se importa automáticamente el formato de listas Android |
| Abreviaturas internas y anotaciones del motor | Incluido | Conservado |
| Símbolos personalizados y emojis | Funciones de NVDA | Conservados mediante pronunciación de símbolos y diccionarios CLDR; sin segunda tabla contradictoria |
| Ocho frecuencias de salida | Incluido | Conservado y probado |
| Retención, lineal, cúbico, sinc, automático | Incluido | Conservado; corrección de desbordamiento de fase y descripción del método efectivo |
| Mejora de nivel y ecualizador de cinco bandas | Incluido | Conservado; estado del filtro mantenido entre piezas de una misma lectura |
| Balance estéreo | Faltaba | Izquierda, centro y derecha; sólo modifica el audio OpenEVV |
| Mantener salida activa y ruido blanco | Faltaba en el panel | Ajustes nativos globales de NVDA, con tiempos de 10, 15, 20, 30, 45, 60, 90, 120, 180, 240 y 300 s, o desactivado |
| Prueba de voz | Dependía del lector | Texto editable y botón de prueba con los ajustes guardados |
| Exportar audio | Faltaba | WAV estéreo PCM16, con ajustes guardados y cancelación; motor aislado para no cambiar la voz activa |
| Interfaz acorde con idioma TTS | Sólo español | Español, inglés, alemán, francés e italiano, seleccionados según la voz configurada al abrir el panel |
| Configuración y perfiles | Incluido | Todos los controles del motor persistentes; ajustes globales identificados como tales |
| Notificación Android con estados corregidos | No corresponde | El panel muestra el método real y la dirección del balance, sin copiar la notificación Android |
| Aviso Android de motor predeterminado y «No mostrar» | No corresponde | NVDA dispone de selección de sintetizador; el complemento no introduce un aviso equivalente |
| Android 17, Gradle, Kotlin, SDK, permisos y pantalla apagada | No corresponde | Biblioteca Windows x64 y API NVDA; la salida sigue disponible con el monitor apagado mientras Windows y NVDA estén funcionando |
| Batería, servicio en primer plano, dos paquetes Android | No corresponde | No se trasladan permisos ni servicios Android a Windows |
| Anticipación de textos largos y límites antiguos de silencio | Retirados del Android actual | Se conserva la cola por piezas, cancelación y vigilancia del controlador; no se presentan preferencias Android que ya no tienen efecto |

## Pausas y orden de procesamiento

En Ajustes de NVDA → OpenEVV → Pausas se eligen los tres modos y el porcentaje
de las pausas explícitas. «Acortar al final» reproduce el comportamiento actual
de Android: añade la instrucción final cuando el texto no termina con una marca
de puntuación; no cambia las pausas internas. «Acortar todas» abrevia también
las marcas de puntuación y los límites de las piezas largas. Un índice al final
de una secuencia no impide aplicar la instrucción de pausa final.

La preparación de SSML, código, números y códigos de verificación utiliza
`filter_speechSequence` antes de que NVDA sustituya los símbolos, para no perder
operadores o contexto. Después actúan los diccionarios y símbolos de NVDA, y
el controlador aplica las anotaciones de deletreo, mayúsculas, prosodia y pausas.
Por ello el orden de los diccionarios respecto a la lectura de código puede
diferir del Android actual. Los nombres radiofónicos y de operadores conservan
las palabras inglesas que emplea su implementación Android.

La salida nativa habitual es de 11.025 Hz. Las frecuencias superiores son
remuestreo y no añaden información acústica original. Python y Kotlin comparten
fórmulas DSP, pero no se afirma igualdad binaria entre ambos procesamientos.
El selector automático usa cúbico en equilibrado, retención en volumen y sinc
en calidad/sin mejora de nivel. No se añadió un algoritmo sin validación.

## Salida activa y exportación

El mantenimiento se delega a `audioAwakeTime` y `whiteNoiseVolume` de NVDA. Es
global, afecta a la salida seleccionada por NVDA y también a otros sintetizadores.
No se altera una preferencia global desde este panel si el usuario no la cambió.
Se conservan valores anteriores superiores a cinco minutos si ya existen;
las nuevas opciones ofrecidas terminan en 300 segundos. Cero de intensidad
usa silencio; un valor mayor añade ruido blanco. La salida puede apagarse si
Windows suspende el equipo, cambia el dispositivo o el propio dispositivo
inalámbrico cierra su enlace. No se promete latencia cero.

La exportación usa una copia temporal privada de la DLL y un hilo independiente.
Conserva la configuración activa del sintetizador; escribe el destino sólo al
terminar y elimina el archivo temporal si se cancela. Se limita a 100.000
caracteres por exportación y se cancela entre piezas. La memoria de la arena
nativa se libera después de descargar la DLL, cuando sus destructores ya han
terminado. Las etiquetas y diccionarios se procesan con los ajustes de NVDA.

## Evidencia y límites de validación

- DLL PE x64 reconstruida con LLVM MinGW, revisión nativa
  `520585138206f1692c619ae2b45856cbbe4255b3`, parches de selector de remuestreo
  y desbordamiento de fase de Android. Sin experimentos WIDE.
- Síntesis real en Windows de las 64 combinaciones idioma/voz y 40 combinaciones
  de frecuencia/método. Índices y audio estéreo comprobados.
- Audio de una frase a 11.025 Hz idéntico al del instalador de ayer en los ocho
  módulos, desactivando las nuevas transformaciones y DSP. No es una prueba
  de igualdad de todas las reglas fonéticas.
- En español, «Hola, mundo. Hola, mundo.» produjo 9.658 muestras en modo normal
  y 5.192 en «Acortar todas». «Hola mundo» produjo 3.883 muestras normales
  y 2.684 con el ajuste final. Son casos de prueba, no porcentajes universales.
- Exportación, cancelación sin sobrescribir el destino y cuatro exportaciones
  repetidas: crecimiento de memoria privada de 0 bytes en la prueba registrada.
- Secuencias de comandos, filtros de texto, persistencia del ecualizador manual,
  panel en cinco idiomas y cooperación con ajustes globales comprobados con
  sustitutos de las API de NVDA/wx. La prueba de widgets no valida su aspecto
  visual ni una sesión real de navegación con el lector.
- Remuestreador C real: cuatro métodos por seis frecuencias, bloques de 50.000
  muestras, continuidad, ganancia de continua, tamaños y límites PCM.
- Contratos de la rama beta oficial NVDA comprobados en el commit
  `8543536502dc30916b6d85a1cc9f397ccef0f9a8`:
  [WavePlayer](https://github.com/nvaccess/nvda/blob/8543536502dc30916b6d85a1cc9f397ccef0f9a8/source/nvwave.py),
  [DriverSetting](https://github.com/nvaccess/nvda/blob/8543536502dc30916b6d85a1cc9f397ccef0f9a8/source/autoSettingsUtils/driverSetting.py),
  [filtros de voz](https://github.com/nvaccess/nvda/blob/8543536502dc30916b6d85a1cc9f397ccef0f9a8/source/speech/extensions.py).

El manifiesto declara compatibilidad NVDA 2026.1–2026.3, sólo x64. No se ejecutó
el complemento dentro de una sesión de NVDA 2026.3. La instalación alpha de
NVDA encontrada en este equipo no se modificó ni se reinició. Los informes
reproducibles están en `tests/*-results.json` y en el archivo de información
del paquete. Esta edición requiere una comprobación final dentro de NVDA.
