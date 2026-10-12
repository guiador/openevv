# Actualización del motor — 9 de octubre de 2026

La revisión de funciones recientes del 10 de octubre añade banda ancha,
base nativa de 16 kHz por instancia, diccionarios de pronunciación de NVDA
y el control de pausas nativas. Las nuevas ediciones son NVDA `0.4.4` y
Android Principal `0.4.3-es`, código 12. Se retira Auxiliar de la distribución.
El tercer parche nativo queda documentado en
`Instaladores/CARACTERISTICAS-RECIENTES.md`; la revisión upstream continúa
siendo `f8a9e91`. Las notas siguientes describen las etapas anteriores.

El 10 de octubre se corrigió la estabilidad de parámetros en el controlador
NVDA y se añadieron créditos y botones de restablecimiento a NVDA y Android.
Los instaladores resultantes son NVDA `0.4.3` y Android `0.4.2-es`, código 11.
El motor nativo no cambió. Véase `Instaladores/CORRECCION-PARAMETROS.md`.

Se actualizaron la copia principal y `free-eloquence-tts-main/native/openevv`
desde [mudb0y/openevv](https://github.com/mudb0y/openevv), de la revisión
`520585138206f1692c619ae2b45856cbbe4255b3` a
`f8a9e91221a15be68eb39845313186c683c1b90d`.

La copia principal coincide con los archivos de esa revisión. La copia nativa
añade únicamente los dos parches locales de audio, adaptados al nuevo
remuestreador de números enteros. Conservan la selección del método de audio,
la protección frente al desbordamiento en bloques grandes y la nueva conversión
de banda ancha a frecuencias inferiores. Se recuperaron las pruebas oficiales
que faltaban en las copias descargadas.

La revisión nativa está registrada en `native/openevv-revision.txt` dentro de la
aplicación Android y se usa como entrada de Gradle para invalidar compilaciones
anteriores. El enlace JNI y el enlace del complemento español utilizan también
la opción `-fno-strict-aliasing` adoptada por el motor.

El complemento español conserva sus personalizaciones. Su generador usa ahora
una copia fija de los dos archivos originales de NVDA de la revisión anterior,
en `nvda-es/upstream`, porque sus transformaciones dependen de esa estructura.
Se ejecutó el generador y se comprobó que sus tres archivos de salida siguen
siendo idénticos. El motor C y el controlador personalizado se actualizan de
forma independiente.

## Verificación

- Compilación Windows x64 con bytecode y los diez idiomas: correcta.
- Compilación JNI Android con los ocho idiomas, para ARM64, ARM de 32 bits y
  x86_64: correcta. Verificación con el NDK 26.1 disponible localmente.
  Posteriormente se recompilaron ambos APK con reglas C; no se ejecutaron
  en un dispositivo.
- Remuestreo: cuatro métodos y seis frecuencias, bloques de 50.000 muestras,
  continuidad, nivel constante, límites PCM y reducción de frecuencia de banda
  ancha: correcto.
- Pruebas oficiales de diccionarios entre idiomas y pausas sobre la DLL nueva:
  correctas.
- Pruebas oficiales de secuencias y del controlador NVDA: correctas.
- Matriz de audio y respuestas: 1.067 de 1.073 casos coinciden. Los 863 casos
  de los ocho idiomas de Android coinciden, al igual que los 105 de polaco.
  En japonés coinciden 99 de 105 casos.

Se reproducen seis fallos de japonés en Windows x64 con bytecode: los casos 5
y 7 de `anno`, `anno3` y `realworld` terminan con acceso inválido
(`0xc0000005`). La reproducción aislada confirma el fallo con el motor original
de la nueva revisión, sin los parches locales. No se considera aprobada la
matriz completa. Se conserva el código original de esa revisión y el detalle
está en `.codex-engine-update/windows-matrix-native.log`.

La matriz utiliza un ejecutor Python local que reproduce la selección de casos
y el cálculo de los dos hashes de `test/matrix.sh`, sobre los archivos de casos
y referencias originales. El script de shell se interrumpió por el coste de
crear miles de procesos en Windows. No se modificaron las referencias.
Esta verificación cubre Windows x64 con bytecode; no se ejecutaron las seis
configuraciones completas de la documentación ni el corpus de palabras.

Se recompilaron los instaladores en `Instaladores`: Android Principal y Auxiliar
`0.4.1-es` y el complemento NVDA x64 `0.4.2`, con reglas C y los ocho idiomas
del producto. Los 863 casos de audio y respuestas coinciden con las referencias
originales para esta compilación C. Las firmas de ambos APK coinciden con sus
versiones anteriores; se verificaron los paquetes y las bibliotecas incluidas.
El detalle está en `Instaladores/RECOMPILACION-MOTOR.md` y los hashes finales
en `Instaladores/instaladores-motor-f8a9e91-info.json`.
En la limpieza posterior se retiraron a la Papelera los instaladores anteriores
y las bibliotecas Android de verificación de `.codex-engine-update/android-jniLibs`,
junto con los temporales de Gradle. Se conservan los tres instaladores nuevos,
las bibliotecas Android actuales de la aplicación, las fuentes y los respaldos.
La DLL de verificación está en `build/eci.dll`.

## Respaldo y evidencia

`.codex-engine-update/root-before.zip` guarda los archivos principales
reemplazados. `local-before` conserva los parches de audio anteriores y
`nvda-generated-before.zip` los archivos generados del complemento español.
La copia nativa conserva además un stash con el estado completo anterior,
incluidas las pruebas ausentes: `Backup before engine update to f8a9e91`.
El directorio `.codex-engine-update` contiene también los registros y scripts
de verificación, los archivos ZIP de ambas revisiones y la copia de consulta
del repositorio bajo `upstream`.
