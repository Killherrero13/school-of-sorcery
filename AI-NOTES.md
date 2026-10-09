# Uso de inteligencia artificial

Utilicé ChatGPT como apoyo para organizar el desarrollo, proponer código
para los modelos, la selección y los endpoints REST, preparar comandos
de prueba y redactar pruebas automáticas y documentación.

Mi trabajo consistió en revisar los requisitos, preparar el entorno local
de Liferay, integrar el código, compilar y desplegar el módulo, ejecutar
las pruebas y comprobar las correcciones. La IA tuvo una participación
importante en el código y las pruebas.

## Ejemplos de peticiones

- «Revisa este error y dime el siguiente paso».
- «Comprueba que la selección cumple las reglas del enunciado».
- «Prepara pruebas para los vetos, las invitaciones y el reparto de casas».

## Errores y correcciones

- La primera propuesta de petición no tuvo en cuenta que el archivo ya
  contenía la propiedad `applications`. Corregimos la estructura tras
  recibir un error 400.
- El código inicial renumeraba a los admitidos en vez de conservar su
  posición en el ranking completo. Lo corregimos y comprobamos que
  A031 conservaba la posición 157 y obtenía plaza por invitación.

## Validación

Las ocho pruebas automáticas pasaron. Con los datos de ejemplo, el servicio
devolvió 50 admitidos y 150 rechazados, respetó la capacidad de las casas
y conservó la última selección tras una petición inválida.