# Configuración local de la aplicación Android

El repositorio conserva el código actual y sus licencias originales. Las claves de firma y las contraseñas locales se mantienen fuera de Git.

La configuración `free-eloquence-tts-main/app/build.gradle.kts` contiene contraseñas de la copia local y está excluida. La plantilla `build.gradle.kts.example` contiene la misma configuración, con contraseñas obtenidas desde propiedades Gradle o variables de entorno.

Al clonar, copia `free-eloquence-tts-main/app/build.gradle.kts.example` a `free-eloquence-tts-main/app/build.gradle.kts`. Para firmar una entrega, proporciona por separado la clave privada `release.jks`, el alias apropiado y las propiedades `releaseStorePassword` y `releaseKeyPassword` en tu archivo personal de Gradle; también se admiten `OPENEVV_STORE_PASSWORD` y `OPENEVV_KEY_PASSWORD`. No guardes esos valores en archivos versionados.

Los instaladores, SDK, entornos locales y resultados de compilación están excluidos. El repositorio no sustituye el respaldo local completo de esos archivos.

La copia nativa de OpenEVV dentro de la aplicación conserva su historial Git local separado. La primera subida del proyecto principal incluirá sus archivos actuales como una instantánea, sin subir aquel historial ni convertirla en un submódulo.
