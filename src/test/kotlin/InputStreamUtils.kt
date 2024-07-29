package stream.tokenArrayInputStream

import java.io.InputStream

inline fun <reified T> T.getResourceAsInputStreamOrThrow(resourcePath: String): InputStream =
    T::class.java.getResourceAsStream(resourcePath)
        ?: error("Рерурс $resourcePath не найден для теста ${T::class.simpleName}")