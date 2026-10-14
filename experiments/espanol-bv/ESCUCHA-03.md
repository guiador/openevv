# Tercera escucha: selección provisional a 250 palabras por minuto

Corrida evaluada: `bv-voicing-01`. Las opciones de la página eran: fricción reforzada como referencia, incremento moderado de sonoridad y un incremento mayor. Se conservaron la duración y los demás parámetros.

| Palabra | Observación del usuario | Alcance de la observación |
|---|---|---|
| vaca | Escuchó igual las tres opciones | Los incrementos no aportaron una diferencia perceptiva clara para el usuario en esta escucha |
| tuvo | La primera opción le pareció la más óptima | Preferencia por fricción reforzada con sonoridad original |
| votar | La segunda opción le pareció antinatural | Valoración desfavorable del incremento moderado; no se comunicó una preferencia específica entre primera y tercera |

Los archivos de audio y las trazas sí registran cambios en AV. Esa diferencia técnica no permite afirmar que el cambio sea perceptible o beneficioso. La escucha no demuestra que las tres opciones de vaca sean acústicamente idénticas, ni establece porcentajes de reconocimiento de b/v.

## Decisión de trabajo

Se mantiene el perfil `friction` como **referencia provisional** a la velocidad habitual de 250 palabras por minuto:

- Fricación: valor interno 52 en el slot 2762 de `span_ph_v`.
- Sonoridad: valores originales 45/50 en el slot 2754.
- Duración: valores originales 50/60 en el slot 2074 de `span_ph_v_dur`.

Los aumentos de sonoridad no se adoptan. La mayor duración tampoco se adopta como base, de acuerdo con la escucha anterior. Se conservan todos los perfiles y audios para consulta; no se modifican los productos distribuidos.

Esta elección expresa una preferencia local entre variantes examinadas, no una validación general de naturalidad o alfabetización acústica. Aún falta comprobar si el perfil elegido distingue b/v de forma fiable frente a los controles y en más palabras y frases. La evaluación a 400 sigue pendiente de oyentes habituados a esa velocidad.

## Siguiente paso antes de ajustar otros parámetros

Evaluar identificación de b/v con la referencia elegida a 250, usando las muestras sin etiquetas ya generadas en `bv-voicing-01/friction/escuchar.html`. Los ejemplos de entrenamiento están en la misma carpeta. Registrar respuestas y comodidad; después ampliar a frases y otros contextos. Si se mantiene una dificultad perceptiva concreta, estudiar un único factor espectral o temporal con una hipótesis explícita.

En esta etapa no se generan más aumentos de amplitud ni de duración: la evidencia disponible favorece conservar el ajuste más sencillo y evaluar su utilidad. Este registro modifica documentación; no cambia reglas, DLL ni audio y no requiere repetir las pruebas nativas ya realizadas para las corridas anteriores.
