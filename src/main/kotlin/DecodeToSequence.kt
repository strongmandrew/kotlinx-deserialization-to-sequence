package stream.arrayTokened

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import java.io.InputStream

@OptIn(ExperimentalSerializationApi::class)
fun <T> Json.decodeArrayToSequence(
    stream: InputStream,
    token: String,
    deserializer: DeserializationStrategy<T>
): Sequence<T> = decodeToSequence(
    stream = stream.arrayTokenized(token),
    deserializer = deserializer,
    format = DecodeSequenceMode.ARRAY_WRAPPED
)