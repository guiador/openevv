# Correcciones y validación del 23 de octubre de 2026

La auditoría recibida sirve como punto de partida, pero no debe aplicarse
literalmente. Se corrigieron defectos concretos en el motor raíz y en la copia
nativa fijada que construye Android y el complemento NVDA. Se conserva la revisión
base `a25d0ee67dd6470198f766995361eabc84e351a8`, las licencias y los avisos.

## Fallo comunicado en Android

El mensaje que comienza por `, APK archivoYT Music Premium v9.41.53` hizo caer
el servicio instalado 0.4.1-es en el teléfono Samsung conectado. La lectura
inválida estaba en `extract`, dentro de `eci_deltadict.c`: una cerca de diccionario
nula terminaba accediendo a la dirección 0x44. Se verificó el lugar mediante el
registro del dispositivo y el desensamblado de su biblioteca original.

La corrección rechaza cercas nulas, anchos inválidos y recorridos incompletos
antes de indexar o dividir. El mismo auxiliar con la implementación anterior
falla en ARM64 real; con la nueva implementación pasa. Esto es distinto del
defecto UTF-8 detectado por la auditoría.

La actualización 0.4.5-es conserva el identificador de paquete y la firma
existente. Es una variante **debug**; no se presenta como APK de publicación.
Las pruebas instrumentadas incluyen el mensaje completo y fragmentos, ambos
españoles, tres modos y la transformación de texto de la aplicación; además
usan el servicio TTS instalado con las preferencias actuales para producir
audio y una frase posterior. No prueban la interacción humana con TalkBack.

## Cambios seleccionados

- Claves de recuperación Delta coherentes con `EVV_AT`, en intérprete y reglas C.
- Propiedad del identificador del hilo en el padre Windows y POSIX; el hijo no
  puede liberar el destino de la escritura del creador.
- UTF-8 incompleto sin leer más allá del terminador; terminación de referencias
  numéricas de cuatro bytes; longitudes y crecimiento de buffers SSML acotados.
- Límites de familia/dialecto en diccionarios activos y liberación de la clave
  temporal propia después de la consulta.
- Sustitución del nombre de archivo Klatt sin perder la asignación anterior y
  conservándola si falla la nueva reserva.
- Remuestreo: el parche de fase existente en la copia nativa también se integra
  en la raíz, sin aplicarlo dos veces.
- Romanizador japonés: el hash puede seleccionar un bloque posterior a los 105
  punteros ingleses generados. Se añade la misma comprobación del número real
  de bloques que ya usan otros diccionarios japoneses. Los seis fallos de la
  matriz estaban aquí; no se atribuyen a Delta sin evidencia.

Los parches `0004`, `0005` y `0006` de `free-eloquence-tts-main/native/patches`
reproducen las nuevas diferencias. La comprobación de fuentes exige igualdad
entre ambos árboles para los archivos corregidos.

## Qué se difiere y por qué

La ruta normal SSML ya toma el mutex global. `evv_land_forget` recibe un rango,
por lo que no se confirma la fuga por llamada que describe la auditoría.
WideBand usa otro objeto Klatt: no se demostró el escenario de `high_ceiling`
que justificaría alterar las tablas o el sonido. No se reemplazan todos los
`abort`, ni se cambia el cursor de archivos sin probar su contrato de anexado.
Las optimizaciones de `fwrite`, el generador aleatorio japonés y el tratamiento
de NUL incrustado se difieren. No se afirma explotación ni estabilidad absoluta.

## Pruebas reproducibles y límites

- `tools/verify-engine-20261012.py`: procedencia, excepciones exactas, parches
  reversibles e igualdad de las correcciones compartidas.
- `tools/check-release-safety.py`: código real SSML con páginas protegidas,
  longitudes, referencias numéricas, 10.000 tareas cortas y extracción de
  diccionario. Se puede elegir cualquiera de los dos árboles con `--engine`.
- `tools/test-windows-safety.ps1`: bibliotecas Windows de la raíz y matriz
  bytecode; `-Only32` comprueba carga, síntesis y cierre x86.
- `tools/check-matrix-host.py`: entradas, modos, audio y respuestas API de la
  matriz original; nunca modifica las referencias. Evita cientos de procesos
  MSYS en un host con poca memoria. Se conservan los fallos de fork originales.
- `free-eloquence-tts-main/tools/verify-updated-native.ps1 -HostMatrix`:
  matriz de los ocho idiomas C del producto y comprobaciones nativas.
- `free-eloquence-tts-main/tools/test-android-crash.ps1`: instrumentación real;
  permite `-SkipBuild` y elegir clase/método con `-TestClass`.

La DLL C de ocho idiomas superó 871 comparaciones de audio y API. El motor
bytecode Windows x64 superó los 1.081 casos de diez idiomas, incluidos los
105 japoneses, sin cambiar referencias. Las DLL x86 y x64 también pasaron
síntesis y cierre delete/live/unload con PCM idéntico. La matriz completa
no se ejecutó en x86.

Las pruebas
del controlador NVDA usan simulaciones de su API: no sustituyen una sesión real
con NVDA ni certifican accesibilidad humana. El texto comunicado también pasó
24 combinaciones idioma/modo en la DLL. El barrido de entradas problemáticas
ejecutó una muestra de 32: el barrido completo de 20.548 se interrumpió por
recursos, por lo que no se declara aprobado.

El host dispone de unos 3,4 GiB de RAM. Se guardaron errores de fork, una
compilación D8 sin memoria y construcciones interrumpidas antes de reducir a un
trabajo nativo y Gradle con un trabajador. No equivalen a resultados funcionales
del motor. Los resultados finales, hashes y límites de cada entrega se guardan
con los paquetes fuera de Git, en `Codex/Entregas/OpenEVV`.

## Contribución al proyecto de origen

Conviene proponer las correcciones mediante PR pequeños con reproducción y
pruebas antes/después. Separar el fallo del diccionario, SSML, vida de hilos y
romanizador facilita su revisión. Las personalizaciones de producto, claves,
binarios y configuraciones privadas no forman parte de esa contribución.
A petición explícita del usuario se abrió el [PR upstream #55](https://github.com/Mudb0y/openevv/pull/55),
en inglés y como borrador, sobre la rama pública actual `d2bc0c68a7769967bb3dfdc86701f69bc53767b4`.
Sus cinco commits contienen sólo motor, pruebas, documentación y CI; no incluyen
el historial privado ni las personalizaciones Android/NVDA. Esa rama superó
1.291 casos Windows x64 bytecode de doce idiomas y 32 combinaciones del mensaje,
además de las pruebas aisladas. Faltan las demás configuraciones exigidas por
upstream; no confundir esta validación con la de las entregas anteriores.
El usuario comunicó mayor rapidez bajo NVDA como observación cualitativa,
sin que se haya realizado una medición comparativa de latencia.
