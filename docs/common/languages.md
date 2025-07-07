# Languages

Базовые классы для создания языковых трансляторов, парсеров и генераторов кода.

## Архитектура

### `LanguageTranslator`
Главный класс транслятора, объединяющий парсер и генератор кода.

**Основные методы:**
* `getMeaningTree(String code)` - парсинг кода в `MeaningTree`
* `getCode(MeaningTree tree)` - генерация кода из `MeaningTree`
* `setConfig(Config config)` - настройка транслятора

### `LanguageParser`
Базовый класс для парсеров исходного кода.

**Ответственность** - преобразование исходного кода в `MeaningTree`

### `LanguageViewer`
Базовый класс для генераторов кода.

**Ответственность** - преобразование `MeaningTree` в код

### `LanguageTokenizer`
> [!TODO]

## Конфигурация

### `Config`
Система конфигурации трансляторов.

**Доступные параметры:**

#### `SkipErrors` (boolean)
Управляет режимом обработки ошибок парсинга.
* `true` - при ошибках всё равно попытаться построить дерево
* `false` - при ошибках трансляция будет прервана
* **Используется в:** `JavaLanguage`, `PythonLanguage`, `CppLanguage` для обработки синтаксических ошибок

#### `ExpressionMode` (boolean) 
Управляет режимом вывода программы.
* `true` - генерировать только одно выражение
* `false` - генерировать полную программу
* **Используется в:** всех языковых модулях для определения формата вывода

#### `TranslationUnitMode` (boolean)
> [!WARNING]
> Параметр определён, но не применяется в коде. Надо удалить?

#### `EnforceEntryPoint` (boolean)
Принуждает к наличию точки входа в программу.
* `true` - требовать точку входа (например main функция/класс)
* `false` - точка входа не обязательна
* **Используется в:** `JavaViewer`, `PythonViewer`, `JavaLanguage` для валидации программ

#### `DisableCompoundComparisonConversion` (boolean)
Отключает преобразование составных сравнений (только для Python).
* `true` - оставлять составные сравнения как есть (`a < b < c`)
* `false` - преобразовывать в логические операции (`a < b and b < c`)
* **Используется в:** `PythonViewer` для обработки цепочек сравнений

## Процесс создания нового языка

### 1. Создание Translator
```java
public class MyLanguageTranslator extends LanguageTranslator {
    public static final int ID = 4;
    
    // Конструкторы
    public MyLanguageTranslator(Map<String, String> rawConfig) {
        super(new MyLanguageParser(), null, rawConfig);
        this.setViewer(new MyLanguageViewer(this));
    }
    
    // ! Стоит вынести из транслятора?
    @Override
    public int getLanguageId() { return ID; }
    
    @Override
    public LanguageTokenizer getTokenizer() {
        return new MyLanguageTokenizer(this);
    }
    
    @Override
    public String prepareCode(String code) {
        // Подготовка кода перед парсингом (например, обёртка для ExpressionMode)
    }
    
    @Override
    public TokenList prepareCode(TokenList list) {
        // Подготовка токенов перед парсингом
    }
}
```

### 2. Создание Parser (наследник LanguageParser)
```java
public class MyLanguageParser extends LanguageParser {
    // Обязательные методы:
    @Override
    public TSTree getTSTree() {
        // Возврат Tree-sitter дерева
    }
    
    @Override
    public abstract MeaningTree getMeaningTree(String code) {
        // Парсинг кода в MeaningTree
    }
    
    @Override
    public abstract MeaningTree getMeaningTree(TSNode node, String code) {
        // Парсинг конкретного узла Tree-sitter
    }
}
```

### 3. Создание Viewer (наследник LanguageViewer)
```java
public class MyLanguageViewer extends LanguageViewer {
    // Обязательные методы:
    @Override
    public String toString(Node node) {
        // Генерация кода из узла MeaningTree
        // Обработка всех типов узлов через switch/if
    }
    

    // ! Нужно вынести в отдельный класс, почему это во Viewer?
    @Override
    public OperatorToken mapToToken(Expression expr) {
        
    }
}
```

### 4. Создание Tokenizer (наследник LanguageTokenizer)
```java
public class MyLanguageTokenizer extends LanguageTokenizer {
    // Обязательные методы:
    @Override
    protected Token recognizeToken(TSNode node) {
        // Распознавание токена из узла Tree-sitter
    }
    
    @Override
    public TokenList tokenizeExtended(Node node) {
        // Расширенная токенизация из MeaningTree узла
    }
    
    @Override
    protected List<String> getOperatorNodes(OperatorArity arity) {
        // Список узлов-операторов по арности
    }
    
    @Override
    protected String getFieldNameByOperandPos(OperandPosition pos, String operatorNode) {
        // Имя поля для позиции операнда
    }
    
    @Override
    protected OperatorToken getOperator(String tokenValue, TSNode node) {
        // Создание операторного токена
    }
    
    @Override
    public OperatorToken getOperatorByTokenName(String tokenName) {
        // Получение оператора по имени токена
    }
}
```

