package stream.tokenArrayInputStream

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import stream.arrayTokened.arrayTokenized
import stream.arrayTokened.decodeArrayToSequence
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalSerializationApi::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SequenceDecodingWithTokenArrayInputStreamTest {

    companion object {
        const val EMPTY_ARRAY_PATH = "/tokenArrayStream/empty.json"
        const val FILLED_ARRAY_PATH = "/tokenArrayStream/filled.json"
        const val CYRILLIC_ARRAY_PATH = "/tokenArrayStream/edgesAfterCyrillic.json"
        const val SPECIAL_CHARSET_ARRAY_PATH = "/tokenArrayStream/specialCharset.json"
        const val ARRAY_NOT_FOUND_PATH = "/tokenArrayStream/object.json"
        const val SPLIT_TOKEN_PATH = "/tokenArrayStream/splitToken.json"
    }

    private val emptyResource = getResourceAsInputStreamOrThrow(EMPTY_ARRAY_PATH)
    private val filledResource = getResourceAsInputStreamOrThrow(FILLED_ARRAY_PATH)
    private val cyrillicResource = getResourceAsInputStreamOrThrow(CYRILLIC_ARRAY_PATH)
    private val specialCharsetResource = getResourceAsInputStreamOrThrow(SPECIAL_CHARSET_ARRAY_PATH)
    private val arrayNotFoundResource = getResourceAsInputStreamOrThrow(ARRAY_NOT_FOUND_PATH)
    private val splitTokenResource = getResourceAsInputStreamOrThrow(SPLIT_TOKEN_PATH)

    @Test
    fun testEmptyArray() {
        val sequence = Json.decodeArrayToSequence(
            stream = emptyResource,
            deserializer = String.serializer(),
            token = "first"
        )

        assertEquals(
            expected = 0,
            actual = sequence.count()
        )
    }

    @Test
    fun testFilledArray() {
        val sequence = Json.decodeArrayToSequence(
            stream = filledResource,
            deserializer = String.serializer(),
            token = "names"
        )

        assertEquals(
            expected = 4,
            actual = sequence.count()
        )
    }

    @Test
    fun testCyrillicArray() {
        val sequence = Json.decodeArrayToSequence(
            stream = cyrillicResource,
            deserializer = String.serializer(),
            token = "edges"
        )

        assertEquals(
            expected = 3,
            actual = sequence.count()
        )
    }

    @Test
    fun testSpecialCharset() {
        val sequence = Json.decodeArrayToSequence(
            stream = specialCharsetResource,
            deserializer = Int.serializer(),
            token = "timestamps"
        )

        assertEquals(
            expected = 1,
            actual = sequence.count()
        )
    }

    @Test
    fun testArrayNotFoundThrows() {
        assertThrows<Exception> {

            Json.decodeArrayToSequence(
                stream = arrayNotFoundResource,
                deserializer = String.serializer(),
                token = "dealers"
            )

        }
    }

    @Test
    fun testSplitToken() {
        /* Должны обрабатываться случаи, когда считанные байты заканчиваются на полуслове:
            {
                "nodes" [
                    {}, {}, {}, {}
                ],
                "edg(/)es": [
                    {}, {}
                ]
            }

            Если первое считывание закончится на подстроке "edg", а второе на "es", то
            токен "edges" не будет распознан и массив не будет найден
         */

        val searchedToken = "edges"

        val splitTokenStream = splitTokenResource.arrayTokenized(searchedToken)
        val readAmount = 16

        val bytes = ByteArray(readAmount)

        val readStrings = mutableListOf<String>()

        assertDoesNotThrow {
            while (splitTokenStream.read(bytes, 0, readAmount) != -1)
                readStrings += String(bytes, Charsets.UTF_8)
        }

        assertTrue(readStrings.isNotEmpty())
    }
}