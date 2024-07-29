package stream.arrayTokened

import org.apache.commons.io.input.BOMInputStream
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Производит фильтрацию входящего потока байт до вхождения [token] и до окончания массива JSON, который
 * [token] начинает
 */
private class TokenArrayFilteringInputStream(
    private val inputStream: InputStream,
    token: String
) : FilterInputStream(inputStream) {

    init {
        require(token.trim().asSequence().none(Char::isWhitespace)) {
            "Для точного поиска входящий токен не должен содержать внутри себя пробелов и пр. символов `whitespace`. Был предоставлен $token"
        }
    }

    companion object {
        const val START_OF_JSON_ARRAY = '['
        const val END_OF_JSON_ARRAY = ']'
        const val JSON_STRING_QUOTE = "\""
    }

    private val jsonFormattedToken = token.run { removeSurrounding(JSON_STRING_QUOTE) }
        .run { "$JSON_STRING_QUOTE$token$JSON_STRING_QUOTE: ?" }.toRegex()

    private val arrayStarted = AtomicBoolean(false)
    private val isEndOfArray = AtomicBoolean(false)
    private val bracesStack = Stack<Char>()

    private val charsetFromFile: Charset = inputStream.markReset(8) {
        BOMInputStream(inputStream).bomCharsetName?.also {
            mark(8) /* обновление маркера на текущую позицию, чтобы снова не читать BOM, когда он найден */
        }?.takeIf(Charset::isSupported)
    }?.let(Charset::forName) ?: Charset.defaultCharset()

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (isEndOfArray.get())
            return -1

        return inputStream.markReset(len) {

            val parsedString = readString(off, len) ?: return -1

            val shiftedStringAmount = parsedString.sizeBeforeEndingShift()

            val shiftedString = parsedString.take(shiftedStringAmount)

            if (arrayStarted.get()) {
                return@markReset 0 to shiftedString.substringBeforeLastBraceIndex().sizeInBytes()
            }

            val obtainedMatch = jsonFormattedToken.find(shiftedString)

            if (obtainedMatch != null) {

                arrayStarted.set(true)

                /* нахождение подстроки, содержащую токен, которую необходимо обрезать с начала */
                val skippedAmount = obtainedMatch.range.last

                val skippedSubstring = shiftedString.take(skippedAmount)

                return@markReset skippedSubstring.sizeInBytes() to shiftedString.drop(skippedAmount)
                    .substringBeforeLastBraceIndex().sizeInBytes()

            } else {
                reset()
                /* необходимо пропустить байты до начала полноценного слова для последующего считывания */
                val skippedBytesAmount = shiftedString.sizeInBytes()
                skip(skippedBytesAmount.toLong())
                return this@TokenArrayFilteringInputStream.read(b, off, len)
            }

        }.let { (skippedAmount, updatedLength) ->
            skip(skippedAmount.toLong())
            inputStream.read(b, off, updatedLength)
        }
    }

    /**
     * Возвращает размер укороченной строки.
     * Это упрощает поиск [token], так как строка не будет заканчиваться на полуслове
     */
    private fun String.sizeBeforeEndingShift(): Int = asSequence().indexOfLast { char ->
        char.isWhitespace() || char.isIdentifierIgnorable()
    }.takeUnless { it < 0 }?.inc() ?: length

    /**
     * Накапливает символы начала и конца JSON массива в стеке. Если извлекается последняя квадратная скобка из стека,
     * то это признак окончания массива: в этом случае возвращается индекс следующий после квадратной скобки, иначе длина исходной строки.
     * Именно так определяется, нужно ли дальше читать байты из потока, пока массив ещё не закончен.
     */
    private fun String.substringBeforeLastBraceIndex(): String {
        forEachIndexed { index, nextValue ->

            when (nextValue) {
                START_OF_JSON_ARRAY -> bracesStack.push(nextValue)
                END_OF_JSON_ARRAY -> {
                    bracesStack.pop()

                    if (bracesStack.empty()) {
                        isEndOfArray.set(true)
                        return substring(0, index + 1)
                    }
                }
            }
        }

        return this
    }

    private inline fun <T> InputStream.markReset(len: Int, action: InputStream.() -> T): T {
        require(markSupported()) {
            "Операция mark/reset не поддерживается текущей реализацией `InputStream`. Выберите другой вариант"
        }

        mark(len)

        return action().also { reset() }
    }

    private fun InputStream.readString(offset: Int, len: Int): String? {
        val tempBytes = ByteArray(len - offset)

        val readAmount = read(tempBytes, offset, len)

        return String(tempBytes, charsetFromFile).takeIf { readAmount != -1 }
    }

    private fun String.sizeInBytes(): Int = toByteArray(charsetFromFile).size
}

internal fun InputStream.arrayTokenized(token: String): InputStream =
    this.takeIf { it is TokenArrayFilteringInputStream } ?: TokenArrayFilteringInputStream(this, token)