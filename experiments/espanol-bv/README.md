# Laboratorio de español ortográfico: primer contraste b/v

Rama de trabajo: `codex/espanol-experimental`. El objetivo de esta etapa es comprobar si una diferencia acústica ayuda a reconocer b/v por oído, conservando comprensión y comodidad. Se mantiene `main` como línea de mantenimiento. El primer experimento usa el motor y la DLL existentes; el banco de pruebas es independiente de NVDA y Android.

## Qué se implementó

Un corpus manual de tres pares: baca/vaca, botar/votar y tubo/tuvo. Para cada palabra se generan lectura normal, transcripción fonética de control y transcripción experimental. En el control cada par comparte la misma pronunciación. En la versión experimental se reemplaza únicamente un símbolo interno `b` o `B` por `v` cuando la palabra está escrita con v. Las palabras con b mantienen exactamente su transcripción.

La existencia de `v` se verificó en `lang/esus/esus.statements`, con reglas acústicas en `lang/esus/rules/ss_val.dr` y de duración en `ss_dur.dr`. El símbolo interno no debe interpretarse automáticamente como una medición IPA ni como un microcontraste ya calibrado. La prueba permite escuchar lo que el motor realmente produce.

Se usa español americano (`esus`), voz predefinida 1, PCM mono de 16 bits a 11.025 Hz, con velocidad configurada de 180 y 400 palabras por minuto. No se añade remuestreo, banda ancha ni procesamiento neuronal. Las transcripciones son explícitas: este prototipo todavía no interpreta texto libre ni cambia reglas Delta. El comportamiento predeterminado de los productos se conserva.

## Respaldo y reproducción

Ejecutar desde la raíz del repositorio con Python de 64 bits en Windows y biblioteca estándar. La DLL predeterminada es la que está en el complemento NVDA local; se puede seleccionar otra con `--dll`. El idioma admite `--language esus` o `eses`; cada corrida evalúa uno solo.

```powershell
python experiments/espanol-bv/run.py snapshot --out build/espanol-experimental/respaldo-nuevo
python experiments/espanol-bv/run.py generate --out build/espanol-experimental/bv-nuevo
python -m unittest discover -s experiments/espanol-bv -p test_experiment.py
```

Cada destino debe ser nuevo para conservar las evidencias anteriores. El respaldo guarda los archivos versionados tal como están en disco y los archivos nuevos no ignorados, con hashes verificados dentro del ZIP. Registra HEAD, rama y eliminaciones. Excluye el directorio `.git`, instaladores y claves ignoradas; se conserva localmente bajo `build`, sin publicarlo. No sustituye un commit ni un respaldo completo del equipo.

El generador crea 36 WAV, un informe con hashes de DLL, corpus, generador y PCM, controles de integridad, una página de entrenamiento y 24 muestras con nombres neutros en orden mezclado. Cada síntesis usa un proceso nuevo con límite de 30 segundos; un error nativo detiene la corrida y deja `failure.json`. Se eliminan del entorno del proceso hijo las variables `EVV_*` para evitar ajustes externos ocultos. El tiempo de proceso registrado incluye el arranque de Python y la DLL: no mide latencia de streaming.

## Cómo escuchar y registrar resultados

1. Abrir `entrenar.html` dentro de la carpeta de la corrida y escuchar los ejemplos con etiquetas. Confirmar que las palabras se entienden y familiarizarse con el sonido experimental.
2. Abrir `escuchar.html`, escuchar las muestras y responder b o v. Se puede dejar una respuesta en blanco. El formulario permite registrar fatiga o dificultad. Las respuestas no se guardan automáticamente: usar el botón para descargar `respuestas.csv` antes de cerrar la página.
3. Conservar el CSV junto con la corrida y puntuarlo:

```powershell
python experiments/espanol-bv/run.py score --run build/espanol-experimental/bv-nuevo --responses C:/ruta/respuestas.csv
```

El informe separa exactitud y respuestas por condición y velocidad; los blancos cuentan como no acertados en el denominador total. `clave-privada.json` contiene las respuestas y condiciones: no consultarlo durante la escucha. Los audios repetidos y el corpus pequeño hacen de esta una exploración personal; no es un estudio ciego formal ni una demostración de alfabetización.

## Criterios para avanzar

- Integridad: audio no vacío, formato correcto y cero fallos de síntesis. Las b deben tener PCM idéntico entre control y experimento, y las v deben producir una diferencia en ambas velocidades.
- Escucha: registrar si se distinguen las letras después del entrenamiento y comparar los aciertos con el control a cada velocidad. Una diferencia de hashes por sí sola no permite aprobar este criterio.
- Usabilidad: anotar palabras que no se entiendan, confusiones, necesidad de repetir y fatiga. Cualquier empeoramiento exige revisar el contraste antes de extenderlo.
- Decisión: repetir con otra semilla y ampliar palabras y frases si hay una mejora consistente y cómoda. Con seis respuestas por condición/velocidad no se fija un umbral de eficacia educativa.

## Etapas posteriores

1. Ajustar o descartar el contraste con la evaluación auditiva del usuario.
2. Ampliar el corpus a frases, posiciones y velocidades habituales; añadir registros de comprensión y comodidad.
3. Si la hipótesis se sostiene, diseñar una regla optativa con correspondencia entre grafema y fonema. Antes de modificar Delta se trazará el recorrido de la letra v hasta los parámetros acústicos, y se ejecutarán las comprobaciones nativas requeridas por el repositorio.
4. Solo después considerar otros contrastes, un frontend nuevo o integración en NVDA/Android. Rust, redes neuronales y reescritura del sintetizador quedan como decisiones pendientes de evidencia.

## Procedencia

El código del banco y sus transcripciones manuales son trabajo nuevo del experimento. La DLL, las reglas y los datos lingüísticos conservan la procedencia y condiciones descritas en `README.md` y `NOTICE` del repositorio. Este banco no convierte esos datos en material independiente ni extiende la licencia MIT a ellos.
