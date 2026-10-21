# Revisión oficial del 12 de octubre de 2026

Se comparó `f8a9e91221a15be68eb39845313186c683c1b90d` con el último HEAD
consultado del repositorio oficial, `a25d0ee67dd6470198f766995361eabc84e351a8`.
Hay **16 commits nuevos** y **73 archivos modificados o añadidos**.

Las fuentes de la raíz y de `free-eloquence-tts-main/native/openevv` se
actualizaron a esa revisión. Se conservaron los tres parches locales:
remuestreo seleccionable, corrección de desbordamiento de fase y base nativa
experimental de 16 kHz. Además se añadió el control de símbolos de NVDA
y una página de símbolos y puntuación en Android, con nombres según el idioma
de la voz. Se conservaron las preferencias, créditos y restablecimientos.

## Cambios relevantes para la edición en español

- **Banda ancha con vocales más completas.** El sintetizador complementario
  utiliza también los formantes sexto a octavo para extender las vocales
  por encima de la zona de unión. Afecta al modo Banda ancha ya disponible;
  el modo habitual permanece separado de esa mejora.
- **Eliminación de clics al finalizar o pausar en banda ancha.** Se corrige el
  sesgo de redondeo de los resonadores de la componente superior.
- **Cierre y descarga de la DLL en Windows.** Upstream corrige una devolución
  de almacenamiento local de hilos durante `FlsFree`, que podía provocar
  bloqueos, fallos al salir y corrupción al descargar la DLL. Es especialmente
  relevante para NVDA y la exportación WAV, que descarga una copia privada.
- **Pronunciación inglesa mejorada**, disponible al utilizar la voz inglesa:
  diccionario de EE. UU. combinado de IBM 4.3 y ETI 6.1, nombres y acentos,
  homógrafos, palabras con «neuro», emoticonos, «vis-à-vis», teléfonos,
  fechas tras nombres de meses, monedas y cadenas de números con guiones.
- **Herramientas y compatibilidad:** nueva información de versión de la DLL,
  compilación y pruebas de cierre con el entorno de hilos utilizado por las
  versiones oficiales, y mejoras del levantamiento de reglas inglesas.

No se encontraron nuevos controles NVDA ni cambios de reglas polacas o
japonesas en estos 16 commits. Se mantiene la selección de ocho idiomas
de nuestra edición; esta revisión no añade esas dos variantes.

## Compilación y validación

Se actualizaron las fuentes y los instaladores: Android Principal 0.4.4-es
y NVDA 0.4.5 contienen el motor `a25d0ee`. Los paquetes anteriores y sus
informes se conservaron en build/upstream-review-20261012/previous-installers.
La nueva matriz pasó 871 casos sin cambios
respecto a las referencias oficiales de esta revisión. Se probaron banda
ancha, cierre de DLL, estabilidad, diccionarios y WAV. Los informes de cada
paquete corresponden a los binarios actuales; no se reutilizan los hashes
de la DLL anterior. No se probó hardware Android ni una sesión real de NVDA.

Se comprueba la igualdad de todos los archivos rastreados con upstream,
permitiendo únicamente los archivos de nuestros parches, la aplicabilidad
inversa de los tres parches y la sintaxis Python. No se presenta esa revisión
de fuentes como una prueba de ejecución del nuevo motor.

Se conserva además la ampliación local de `.gitignore`, que excluye herramientas,
entregas y configuración privada; sus reglas upstream originales permanecen.

El constructor NVDA se preparó para incluir el recurso oficial de identidad
y versión de la DLL. El empaquetador impide utilizar las regresiones de una
revisión anterior como evidencia del motor nuevo.

Se recompilaron la DLL y las tres bibliotecas Android y se ejecutaron las
regresiones de parámetros, modos, diccionarios y WAV, con las referencias
nuevas de la matriz. También pasaron los arneses oficiales de banda ancha
y de cierre de DLL en Windows. Los hashes de banda ancha cambian intencionadamente en
upstream; no deben compararse contra las referencias de `f8a9e91`.

El respaldo de los archivos reemplazados y el registro de integración están
en `build/upstream-review-20261012`. Los scripts de actualización y revisión
están en `tools/update-engine-20261012.py` y `tools/verify-engine-20261012.py`.

Referencias oficiales: [comparación de commits](https://github.com/mudb0y/openevv/compare/f8a9e91221a15be68eb39845313186c683c1b90d...a25d0ee67dd6470198f766995361eabc84e351a8),
[clics de banda ancha](https://github.com/mudb0y/openevv/commit/59591f1353d56dbadcb60956ca4b988f840c1ce9),
[cierre de DLL](https://github.com/mudb0y/openevv/commit/39498f156d366aee478a481cefedbf6955e46f76).
