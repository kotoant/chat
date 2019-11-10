# Тестовый чат

## Подготовка
Запускаем базу данных
```bash
docker container run --detach --publish 27017:27017 mongo
```

## Сборка приложения с тестами
```bash
gradle clean build fatJar
```

## Запуск приложения
```bash
gradle run
```
```bash
java -jar build/libs/chat-fat-1.0-SNAPSHOT.jar
```

## Использование
### Клиент
[http://localhost:8082/](http://localhost:8082/)

### История сообщений
[http://localhost:8081/getHistory](http://localhost:8081/getHistory)

### Загрузка изображений на сервер
 [http://localhost:8082/upload-image.html](http://localhost:8082/upload-image.html)

### Список загруженных изображений
 [http://localhost:8081/images/](http://localhost:8081/images/)
 
 ### Скачать загруженное изобоажение
  [http://localhost:8081/images/imageId](http://localhost:8081/images/imageId)