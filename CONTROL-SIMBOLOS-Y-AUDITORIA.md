# Símbolos, puntuación y revisión de funciones — 12 de octubre de 2026

En Android, **Ajustes → Lectura de textos → Símbolos y signos de puntuación** ofrece
Todos, La mayoría, Algunos, Personalizado y Ninguno. Personalizado permite
escribir los signos elegidos. Restablecer recupera Algunos y vacía la lista;
el cambio se guarda al pulsar Guardar, mientras Cancelar lo descarta.
Se conservan las preferencias anteriores al actualizar.

El control Android procesa el texto recibido por el TTS. Si una aplicación
como un lector de pantalla ya convierte un signo en palabras antes de enviarlo,
hay que ajustar también el nivel de puntuación de esa aplicación; el motor
recibe palabras y no puede distinguirlas de texto escrito por una persona.

Los nombres añadidos a la lectura siguen el idioma de la voz, no el de la
interfaz: español de España y México ya pronuncian «coma», «punto» y
«abre paréntesis», en vez de nombres en inglés. También se reconocen los
signos iniciales ¿ y ¡. Se mantienen las reglas del motor para cifras,
decimales, porcentajes y monedas, así como sus anotaciones internas.

En **NVDA → Preferencias → Opciones → OpenEVV → Lectura** hay un selector de
verbalización con Ninguno, Algunos, La mayoría y Todos. Comparte el ajuste
de Voz del perfil actual de NVDA; afecta también a otros sintetizadores.
Aplicar o Aceptar guarda; Cancelar descarta. La personalización de cada
símbolo permanece en el diálogo oficial Pronunciación de símbolos de NVDA.
Las pruebas de voz y los WAV usan el nivel guardado. No se añade un segundo
procesamiento de símbolos sobre los nombres que NVDA ya pronuncia.

Estos niveles y su configuración proceden de la
[implementación oficial de NVDA](https://github.com/nvaccess/nvda/blob/master/source/characterProcessing.py).

## Funciones revisadas

| Función | Android principal | Complemento NVDA |
|---|---|---|
| Símbolos y puntuación | Página propia, cinco modos y lista personalizada | Cuatro niveles oficiales, personalización mediante NVDA |
| Emojis | Interruptor y nombres según idioma | Descripciones y diccionarios de símbolos de NVDA |
| Números y códigos de verificación | Modos normal, cifras, pares y ternas; códigos | Controles equivalentes |
| Deletreo y mayúsculas | Cinco modos y anuncio de mayúsculas | Controles propios y órdenes de NVDA; tono temporal restaurado |
| Lectura de código | CamelCase, snake_case, operadores, llaves, sangría, rutas y URL | Controles equivalentes |
| Diccionarios y abreviaturas | Editores propios y diccionarios del motor | Diccionarios NVDA más conjuntos nativos y entradas personales |
| Pausas | Normales, finales o todas; predicción de frases | Pausas nativas y porcentaje de pausas explícitas NVDA |
| Voz y timbre | Presets y restablecimiento de la voz seleccionada | Presets y restablecimiento al aplicar |
| Ecualizador | Cinco bandas y restablecimiento neutro | Cinco bandas y restablecimiento neutro |
| Calidad y remuestreo | Ocho frecuencias, métodos y balance | Controles equivalentes |
| Banda ancha y base nativa 16 kHz | Modos experimentales optativos | Modos experimentales optativos por perfil; también WAV |
| Respuesta experimental | Página con perfiles de preparación y reproducción | Fragmentos, bloques nativos y entrega de audio; perfiles y desactivación |
| Prueba y WAV | Prueba de voz y exportación | Prueba y exportación cancelable con motor independiente |
| Salida activa | Opciones de Android | Tiempo de salida activa y ruido blanco de NVDA |
| Sistema y notificaciones | Permisos, batería, notificación y rutas de Android | Se utilizan dispositivo y salida de NVDA/Windows |
| Créditos | Acerca de | Acerca de y manifiesto |

La actualización oficial `a25d0ee` añade mejores reglas inglesas para dinero,
fechas, teléfonos, nombres y palabras compuestas; corrige clics de banda ancha,
mejora formantes superiores y corrige el cierre de la DLL en Windows. Son
cambios del motor, sin nuevos interruptores obligatorios.
La lista de commits está en `REVISION-UPSTREAM-2026-10-12.md`.

Polaco y japonés continúan fuera de estos dos paquetes: esta actualización
no modifica sus reglas y no acredita su compilación y validación en las tres
arquitecturas Android. Las ocho variantes distribuidas conservan 64 voces.

## Verificación

El panel se comprueba mediante sus métodos reales con sustitutos de wx/NVDA;
el código Kotlin de símbolos se ejecuta en JVM con las mismas aserciones de
la prueba de instrumentación. Eso no equivale a una prueba con TalkBack o
una sesión real de NVDA. Los informes de compilación y del motor deben
corresponder a la nueva DLL y a las bibliotecas antes de sustituir instaladores.

El arnés oficial de banda ancha se ejecuta sobre una copia con una única
adaptación: el primer modo inválido es 3, puesto que nuestro parche reserva
2 para la base nativa de 16 kHz. No se cambian sus límites ni comprobaciones
de audio. El código oficial permanece intacto y se conserva el registro de
la primera ejecución que detectó esta diferencia de contrato.
