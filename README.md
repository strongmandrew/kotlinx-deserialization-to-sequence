# Доработка десериализации в Sequence из библиотеки Kotlinx Serialization 
В библиотеке Kotlinx Serialization метод десериализации потока `InputStream` в поток `Sequence` для массивов работает только если поток `InputStream` начинается непосредственно с массива (`[]`). 
Это создаёт неудобства, если цель десериализовать массив из произвольной вложенной структуры. 
Например, имея следующую структуру класса в Kotlin:
```kotlin
@Serializable
class User(
  private val login: String,
  private val id: Long,
  private val loginHistory: List<>
)

@Serializable
data class LoginHistory(
  private val timestamp: Long,
  private val ip: String
)
```

Необходимо десериализовать только массив `loginHistory` и сделать это лениво (не десериализуя целиком `User`, предположим, в массиве 1_000_000 вхождений класса `LoginHistory`):
```json
{
  "login": "john-goodrow",
  "id": 25,
  "loginHistory": [
    { "timestamp": 4685416846, "ip": "102.214.56.3" },
    { "timestamp": 1684684877, "ip": "102.214.56.3" },
    { "timestamp": 1618168468, "ip": "102.215.58.1" },
  ]
}
```

Для этого был добавлен экстеншн на `Json` для десериализации вложенного массива следующим образом:
```kotlin
val loginEntries: Sequence<LoginHistory> = Json.decodeArrayToSequence(
  stream = incomingStream,
  deserializer = LoginHistory.serializer(),
  token = "loginHistory"
)
```
