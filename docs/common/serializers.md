# Serializers

Сериализаторы для экспорта `MeaningTree` в различные форматы.

## Поддерживаемые форматы

### JSON Serializer
Экспорт в структурированный JSON формат.

**Использование:**
```java
JsonSerializer serializer = new JsonSerializer();
JsonObject json = serializer.serialize(meaningTree.getRootNode());
String jsonString = new GsonBuilder()
    .setPrettyPrinting()
    .create()
    .toJson(json);
```

### RDF Serializer
Экспорт в формат RDF/XML для семантического анализа.

**Использование:**
```java
RDFSerializer serializer = new RDFSerializer();
Model model = serializer.serialize(meaningTree.getRootNode());
StringWriter writer = new StringWriter();
model.write(writer, "RDF/XML");
```

## Архитектура

### IOAliases
Система псевдонимов для различных форматов ввода-вывода.

**Функциональность:**
* Регистрация сериализаторов по имени
* Единый интерфейс для всех форматов

### IOAlias
Отдельный псевдоним для конкретного формата.

**Компоненты:**
* Имя формата (например, "json", "rdf")
* Функция сериализации
* Обработка ошибок

## Добавление нового формата

1. **Создание сериализатора**
   ```java
   public class MyFormatSerializer {
       public String serialize(Node rootNode) {
           // логика сериализации
       }
   }
   ```

2. **Регистрация в системе**
   ```java
   new IOAlias<>("myformat", node -> {
       MyFormatSerializer serializer = new MyFormatSerializer();
       return serializer.serialize(node);
   })
   ```

## Зависимости

* **Gson** - JSON сериализация
* **Apache Jena** - RDF/XML сериализация