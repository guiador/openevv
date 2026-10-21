# Continuidad — openevv-main

Actualizado el 20 de octubre de 2026. Motor OpenEVV y adaptaciones Android/NVDA con español experimental.

## Punto de partida

Repositorio de trabajo: https://github.com/guiador/openevv (private). Se conserva el historial y la procedencia; esta organización no convierte los componentes de terceros en código propio.

src/lang: motor y módulos; free-eloquence-tts-main: Android, copia nativa fijada y NVDA; tools/test: generación y comprobaciones; Instaladores: entregas.

## Construcción y comprobación

Leer CLAUDE.md, docs/building.md y PREPARACION-GITHUB.md. Conservar las revisiones nativas y los parches locales; usar la plantilla de configuración sin credenciales al clonar.

La matriz nativa completa se describe en CLAUDE.md. tools/verify-engine-20261012.py verifica las fuentes; free-eloquence-tts-main/tools/test-symbols-host.ps1 prueba símbolos en JVM.

Comprobaciones ejecutadas durante la preparación de continuidad:

- APROBADA: `python.exe tools/verify-engine-20261012.py` (39.86 s).
- APROBADA: `pwsh.EXE -NoProfile -File free-eloquence-tts-main/tools/test-symbols-host.ps1` (18.08 s).

Las comprobaciones automáticas describen sólo lo ejecutado. No certifican accesibilidad humana, funcionamiento de todos los dispositivos ni equivalencia entre un paquete anterior y el código actual. La evidencia histórica conserva su fecha y versión.

## Entrega y entorno local

En el equipo de trabajo, el índice único es `Codex/Entregas/OpenEVV`. `Codex/Entregas/LEEME.md` y sus manifiestos identifican los paquetes conservados y sus SHA-256. Las entregas anteriores a esta revisión no deben presentarse como compilaciones de este commit. Los paquetes y entornos no forman parte del código fuente.

Las herramientas locales se reúnen en `Codex/Soporte/Herramientas`; los enlaces de compatibilidad conservan las rutas de los scripts. En otro equipo se deben preparar las dependencias indicadas por este proyecto. No copiar ciegamente rutas de Guillermo ni tratar un entorno virtual Windows como un instalador portable.

Las claves de firma y configuraciones privadas se conservan localmente y se excluyen de Git. No se sustituyen para crear una actualización; usar las claves existentes o documentar una nueva identidad de paquete. No incorporar cuentas, bases, fotos ni documentos de usuarios a las pruebas.

## Próximo trabajo útil

Verificar todos los idiomas al modificar el motor; separar cambios acústicos deliberados de regresiones. No regenerar datos de idioma con herramientas que sobrescriban el diccionario. Conservar NOTICE y las diferencias de licencias de código y datos.

Antes de editar, leer el alcance vigente y revisar `git status`. Mantener separados cambios de producto, construcción y limpieza. Conservar las APIs, formatos y licencias salvo que la tarea requiera cambiarlos y se pueda verificar la migración. Las instrucciones posteriores del usuario prevalecen sobre esta nota.

## Referencias

- [README.md](README.md)
- [CLAUDE.md](CLAUDE.md)
- [PREPARACION-GITHUB.md](PREPARACION-GITHUB.md)
- [CONTROL-SIMBOLOS-Y-AUDITORIA.md](CONTROL-SIMBOLOS-Y-AUDITORIA.md)
- [NOTICE](NOTICE)
