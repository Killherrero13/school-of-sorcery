# Uso de inteligencia artificial

Para esta prueba he utilizado ChatGPT como apoyo durante el desarrollo.

## Trabajo de ChatGPT

- Me ayudó a organizar los pasos para desarrollar y desplegar el servicio.
- Propuso código para los modelos, la selección de candidatos y los endpoints REST.
- Preparó comandos para probar el servicio desde PowerShell.
- Revisó el código comparándolo con las reglas del ejercicio.
- Propuso las ocho pruebas automáticas y la documentación.

## Mi trabajo

- Preparé y puse en marcha el entorno local de Liferay.
- Incorporé el código y los cambios al proyecto.
- Compilé y desplegué el módulo.
- Ejecuté las peticiones REST y las pruebas automáticas.
- Compartí el código, los resultados y los errores para revisarlos con ChatGPT.
- Apliqué las correcciones y comprobé su resultado en mi entorno.

La IA tuvo una participación importante en el código y las pruebas. Mi trabajo
se centró en la integración, la ejecución y la comprobación local.

## Ejemplos de lo que pedí

- Ayúdame a continuar con la prueba después de compilar el módulo.
- Revisa este error y dime el siguiente paso.
- Comprueba que la selección cumple las reglas del enunciado.
- Prepara pruebas para los vetos, las invitaciones y el reparto de casas.

## Errores y correcciones

La primera propuesta para construir la petición no tuvo en cuenta que el
archivo de solicitudes ya contenía la propiedad `applications`. Eso generó
una estructura incorrecta y el servicio respondió con un error 400.
Corregimos la preparación de la petición.

También detectamos que el código inicial numeraba a los admitidos dentro
de la lista final, en lugar de conservar su posición en el ranking completo.
Lo corregimos y comprobamos que A031 mantenía la posición 157, aunque
obtenía plaza por invitación.

## Comprobaciones realizadas

- Las ocho pruebas automáticas pasaron, sin errores ni pruebas omitidas.
- El servicio procesó los datos de ejemplo: 50 admitidos y 150 rechazados.
- Las casas respetaron sus límites de plazas.
- La consulta de casas devolvió el resultado de la última selección.
- Una petición inválida devolvió un error 400 sin borrar el resultado anterior.

Estas comprobaciones cubren los casos probados, pero no garantizan que
no pueda existir algún error en otros casos.