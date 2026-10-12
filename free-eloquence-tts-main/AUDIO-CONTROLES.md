# Correcciones de audio y controles

El aviso de motor predeterminado tiene una casilla «No volver a mostrar». La
elección se guarda al cerrar el diálogo, tanto con sus botones como con Atrás
o tocando fuera. No modifica el motor predeterminado del sistema.

## Remuestreo y notificaciones

Los cuatro métodos disponibles siguen siendo retención, lineal, cúbico y sinc.
«Según calidad» ahora muestra entre paréntesis el método que aplica el perfil
de optimización. El menú y la notificación comparten los mismos identificadores
y nombres; los identificadores nativos 0, 2, 3 y 4 no son índices consecutivos.
No se añade un método sin una ventaja de calidad o latencia demostrada.

El acumulador de posición nativo ahora usa 64 bits: a 48 kHz, un bloque de
50 000 muestras de entrada podía sobrepasar el entero de 32 bits. Se rechazan
frecuencias inválidas, conversiones descendentes no admitidas y métodos
desconocidos. El parche `native/patches/0002-resampler-phase-overflow.patch`
conserva la corrección cuando se obtiene de nuevo el submódulo original.

La pantalla recibe los cambios hechos desde la notificación. El balance usa
«Izquierda», «Centro» y «Derecha», y sus botones recorren esas tres posiciones.
Los valores intermedios conservados de versiones anteriores indican el lado
favorecido. Las ganancias muestran dB; la intensidad del ruido, dBFS; y el
plazo, segundos. Los botones de una notificación antigua no modifican otro
parámetro después de cambiar el control seleccionado.

## Mantenimiento Bluetooth

El mantenimiento sigue siendo opcional. Necesita la notificación persistente,
un servicio en primer plano aceptado por Android y reproducción propia en
Accesibilidad o Multimedia. La ruta automática y la reproducción duplicada
no lo activan. Solo emite ruido si la ruta efectiva de AudioTrack es Bluetooth
estéreo; se interrumpe si cambia de salida o comienza una llamada.

Se inicia cuando termina la síntesis, también si se interrumpe una frase.
Se detiene al comenzar la siguiente síntesis y reinicia el plazo al terminarla.
Activarlo o iniciar el servicio de notificación también puede iniciar el plazo
si no hay síntesis. La orden explícita de detener desde la notificación mantiene
el silencio hasta la siguiente petición de voz o hasta volver a activar el
mantenimiento. Las muestras también suspenden el mantenimiento durante su
síntesis y reproducción, y lo reanudan al terminar.

Duraciones: 1, 5, 10, 15, 20, 30, 45, 60, 90, 120, 180, 240 y 300 segundos.
Se mantienen 1 y 5 segundos para compatibilidad. El máximo es cinco minutos
por intervalo sin síntesis, no un temporizador periódico permanente.

Un bloqueo parcial de CPU limitado al plazo mantiene el procesamiento con la
pantalla apagada. Se libera al detenerse y en cualquier salida del hilo; el
bucle de escritura también comprueba el vencimiento para no quedarse activo
si AudioTrack deja de aceptar datos. Se usa `acquire(timeout)` según la
[documentación de Android](https://developer.android.com/develop/background-work/background-tasks/awake/wakelock/set).
Esto puede reducir el tiempo de reactivación de un dispositivo remoto; no
garantiza latencia cero ni elimina la latencia del códec Bluetooth.

## Verificación

`tools/check_resampling.py --cc <compilador C>` ejecuta la implementación
nativa real, aislada de la síntesis. Comprueba cuatro métodos por seis
frecuencias de salida, bloques de 50 000 muestras, continuidad con particiones
irregulares, nivel constante, recuentos y límites PCM de 16 bits.

`AudioControlsTest` comprueba correspondencia de los métodos, selección
automática, etiquetas de balance, pasos de duración, máximo de 300 segundos y
persistencia de la casilla del aviso. Requiere un dispositivo para ejecutarse.

Resultado local: pasa la prueba nativa de los 24 casos y la comprobación de
546 recursos traducibles en cinco idiomas. Se compila el conjunto de pruebas
instrumentadas y se generan ambos APK de depuración con las bibliotecas ARM
recompiladas. Las bibliotecas y el empaquetado conservan alineación de 16 KB.
La recompilación local usó NDK 26.1; el proyecto y la integración continua
siguen fijando r28c, cuya compilación aún requiere validación en este entorno.

La prueba de pantalla apagada debe hacerse en un teléfono con Bluetooth:
activar notificación persistente, elegir Accesibilidad o Multimedia, activar
el mantenimiento y seleccionar un plazo; hablar, bloquear la pantalla y
comprobar el siguiente mensaje antes y después del vencimiento. Verificar
también interrupciones, desconexión, llamada y el botón Detener. No hay un
dispositivo conectado en este entorno para validar ese comportamiento.
