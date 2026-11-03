# Ajuste del hito 1 — Footer visible y medidas relativas

Trabaja en cpz-sim-datacenter-ui, rama config-controls-runtime. Inspecciona los cambios locales antes de editar y conserva el trabajo existente. No hagas commit ni push.

## Estado y objetivo
El hito 1 incorpora RuntimeSketch, RuntimeLayout y RuntimeView, seleccionables desde Launcher con -Dsimulation.source=RUNTIME. El modo DEMO sigue siendo predeterminado y DATASET conserva su ruta. Según la ejecución anterior, mvn test pasó 36 pruebas con JDK 26.0.1.
En la captura del usuario las áreas superiores se ven correctamente, pero el footer queda cortado en el borde inferior de la pantalla. La superficie solicitada era 1920 × 993 y en una prueba el gestor de ventanas la cambió a 1920 × 1014. Estos datos son observaciones, no una causa confirmada.
El usuario ya cambió el label de cabecera: conserva su texto actual.
Corrige el corte, respetando la convención de medidas y sin implementar el hito 2.

## Convención obligatoria
La configuración de geometría usa medidas relativas entre 0 y 1:
- Posición X relativa al ancho de la superficie.
- Posición Y relativa al alto de la superficie.
- Tanto ancho como alto relativos al ALTO de la superficie.
- Márgenes, separaciones y otras longitudes de distribución deben seguir la misma referencia al alto.
Los píxeles se calculan únicamente en el punto que los necesite la API de dibujo. No almacenes el layout con constantes de píxeles ni normalices el ancho respecto del ancho de pantalla.
Consulta primero la documentación de diseño y la API de la versión efectiva de cpz-mvvm-processing-controls. Utiliza sus bounds y medidas públicas de acuerdo con esa convención, sin aplicar conversiones dos veces ni asumir su semántica por el nombre.
Si hace falta un parche en la biblioteca, detén la parte afectada y explica la evidencia; no cambies la biblioteca ni su versión.

## Trabajo
1. Revisa RuntimeSketch, RuntimeLayout y RuntimeView y localiza su geometría y gestión de superficie. Verifica sus rutas reales.
2. Distingue dos casos: contenido fuera de los límites reales del canvas, o canvas/ventana mayor que el área útil del escritorio. Comprueba width/height efectivos frente al tamaño solicitado y considera las decoraciones y paneles del escritorio. No concluyas que hay píxeles fijos sin verificarlo.
3. Corrige cualquier incumplimiento de la convención en la nueva UI. Mantén límites compartidos para que marcos, etiquetas y futuros controles utilicen la misma geometría. Recalcula al cambiar el tamaño efectivo cuando corresponda.
4. Asegura que las ocho áreas, incluido el borde inferior del footer, quedan completas dentro del canvas con margen inferior y sin solapamientos. Conserva la distribución general y ajusta las proporciones solo cuando sea necesario.
5. Si la ventana excede el área útil, adapta únicamente el arranque runtime para que la ventana decorada quepa en ella, respetando la resolución configurada cuando ya cabe. Usa las capacidades públicas compatibles con el renderer y el entorno; evita offsets mágicos para una sola máquina, bucles de redimensionado y cambios en el modo clásico. No añadas dependencias.
6. No ocultes el problema mediante recorte del dibujo, eliminación del footer o desplazamiento fuera de vista de otras áreas.
7. Actualiza docs/runtime-controls-progress.md o el documento equivalente existente con la causa encontrada, cambio, validaciones y pendientes. Mantén pendiente la aceptación visual del usuario si no puedes comprobarla.

## Validación
- Ejecuta mvn test con el JDK requerido por el pom actual.
- Añade pruebas de geometría si aportan cobertura real: áreas dentro del canvas, sin solapamientos y ancho/alto escalados por la altura, usando distintas relaciones de aspecto.
- Comprueba manualmente runtime en la resolución configurada y una ventana menor si el entorno gráfico lo permite. Verifica footer completo, títulos legibles y ausencia de solapamientos.
- Si no hay entorno gráfico, declara esa limitación y entrega pasos breves para la comprobación del usuario. No afirmes que la validación visual pasó.
- Conserva los modos DEMO y DATASET, sus recursos y comportamiento.

## Entrega breve
Informa causa confirmada (o incertidumbre restante), archivos afectados, convención aplicada, comandos y resultados, comprobaciones visuales pendientes y mensaje de commit sugerido. El siguiente paso sigue siendo generar el mapa desde el layout en el hito 2; no lo implementes.

