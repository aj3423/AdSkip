package ad.skip.query

import java.util.regex.PatternSyntaxException
import kotlin.math.max
import kotlin.math.min

internal data class ParsedQuery(
    val clauses: List<QueryClause>
)

internal data class NodePath(
    val traversals: List<TraversalStep>,
    val field: String
) {
    // Rebuild the user-facing query text from the parsed path, for example:
    // [Sibling(-2)] + "text" -> sibling[-2].text
    fun render(): String =
        (traversals.map { it.render() } + field).joinToString(".")
}

internal sealed interface TraversalStep {
    fun render(): String

    data object Parent : TraversalStep {
        override fun render(): String = "parent"
    }

    data class Child(val index: Int) : TraversalStep {
        override fun render(): String = "child[$index]"
    }

    data class Sibling(val offset: Int) : TraversalStep {
        override fun render(): String = "sibling[$offset]"
    }
}

internal enum class ComparisonOperator(val symbol: String) {
    EQ("="),
    GT(">"),
    GTE(">="),
    LT("<"),
    LTE("<="),
}

internal sealed interface QueryClause {
    val path: NodePath
    fun render(): String
}

internal enum class StringMatchMode {
    EXACT,
    REGEX
}

internal data class StringQueryClause(
    override val path: NodePath,
    val value: String,
    val matchMode: StringMatchMode,
    val regex: Regex? = null
) : QueryClause {
    override fun render(): String =
        "${path.render()}=\"${value.encodeQuotedQueryValue()}\""
}

internal data class NumericComparisonClause(
    override val path: NodePath,
    val operator: ComparisonOperator,
    val value: Int
) : QueryClause {
    override fun render(): String =
        "${path.render()}${operator.symbol}$value"
}

internal data class NumericRangeClause(
    override val path: NodePath,
    val minValue: Int,
    val maxValue: Int
) : QueryClause {
    override fun render(): String =
        "${path.render()}=$minValue~$maxValue"
}

internal sealed interface QueryParseResult {
    data class Success(val query: ParsedQuery) : QueryParseResult
    data class Error(val message: String) : QueryParseResult
}

internal object NodeQueryParser {
    fun parse(query: String): QueryParseResult {
        if (query.isBlank()) {
            return QueryParseResult.Error("Query is blank")
        }

        return try {
            QueryParseResult.Success(Parser(query).parse())
        } catch (e: ParseException) {
            QueryParseResult.Error(e.message ?: "Invalid query")
        }
    }

    private class Parser(private val text: String) {
        private var index = 0

        // The grammar is intentionally small:
        //   field="value"
        //   field>123
        //   field=100~200
        // joined by `and`, with optional traversal prefixes such as parent. or child[2].
        fun parse(): ParsedQuery {
            val clauses = mutableListOf<QueryClause>()
            skipWhitespace()

            while (!isAtEnd()) {
                clauses += parseClause()
                skipWhitespace()
                if (isAtEnd()) break
                parseAndSeparator()
                skipWhitespace()
            }

            if (clauses.isEmpty()) {
                throw ParseException("Query is blank")
            }

            return ParsedQuery(clauses)
        }

        private fun parseClause(): QueryClause {
            val path = parsePath()
            skipWhitespace()
            val operator = parseOperator()
            skipWhitespace()

            return if (peek() == '"') {
                // Quoted clauses are always string clauses.
                if (operator != ComparisonOperator.EQ) {
                    throw ParseException("Quoted values only support '=' at position ${index + 1}")
                }
                val value = parseQuotedString()
                // `text` is the only string field that intentionally supports regex.
                // Other string fields stay exact matches to avoid surprising rules.
                if (path.field == "text") {
                    val regex = try {
                        Regex(value)
                    } catch (e: PatternSyntaxException) {
                        throw ParseException("Invalid regex for ${path.render()}: ${e.description}")
                    }
                    StringQueryClause(path, value, StringMatchMode.REGEX, regex)
                } else {
                    StringQueryClause(path, value, StringMatchMode.EXACT)
                }
            } else {
                // Unquoted clauses are numeric comparisons or numeric ranges.
                val firstValue = parseInteger()
                skipWhitespace()
                if (operator == ComparisonOperator.EQ && peek() == '~') {
                    index++
                    skipWhitespace()
                    val secondValue = parseInteger()
                    NumericRangeClause(
                        path = path,
                        minValue = min(firstValue, secondValue),
                        maxValue = max(firstValue, secondValue)
                    )
                } else {
                    NumericComparisonClause(path, operator, firstValue)
                }
            }
        }

        private fun parsePath(): NodePath {
            val segments = mutableListOf<PathSegment>()
            segments += parseSegment()
            while (peek() == '.') {
                index++
                segments += parseSegment()
            }

            val fieldSegment = segments.lastOrNull() as? FieldSegment
                ?: throw ParseException("Field path must end with a field name")

            // Everything before the final field name is treated as navigation.
            // Example: parent.parent.text -> [Parent, Parent] + field "text".
            val traversals = segments.dropLast(1).map { segment ->
                when (segment) {
                    is FieldSegment -> when (segment.name) {
                        "parent" -> TraversalStep.Parent
                        else -> throw ParseException("Unsupported traversal '${segment.name}'")
                    }

                    is IndexedSegment -> when (segment.name) {
                        "child" -> {
                            if (segment.index < 0) {
                                throw ParseException("child[...] index must be >= 0")
                            }
                            TraversalStep.Child(segment.index)
                        }

                        "sibling" -> {
                            if (segment.index == 0) {
                                throw ParseException("sibling[0] is not supported")
                            }
                            TraversalStep.Sibling(segment.index)
                        }

                        else -> throw ParseException("Unsupported traversal '${segment.name}[${segment.index}]'")
                    }
                }
            }

            return NodePath(traversals = traversals, field = fieldSegment.name)
        }

        private fun parseSegment(): PathSegment {
            val name = parseIdentifier()
            return if (peek() == '[') {
                // child[...] and sibling[...] both pass through here; parsePath()
                // decides later whether that indexed segment is actually legal.
                index++
                skipWhitespace()
                val parsedIndex = parseInteger()
                skipWhitespace()
                if (peek() != ']') {
                    throw ParseException("Expected ']' at position ${index + 1}")
                }
                index++
                IndexedSegment(name, parsedIndex)
            } else {
                FieldSegment(name)
            }
        }

        private fun parseIdentifier(): String {
            if (isAtEnd() || !peek().isIdentifierStart()) {
                throw ParseException("Expected identifier at position ${index + 1}")
            }

            val start = index
            index++
            while (!isAtEnd() && peek().isIdentifierPart()) {
                index++
            }
            return text.substring(start, index)
        }

        private fun parseOperator(): ComparisonOperator =
            when {
                match(">=") -> ComparisonOperator.GTE
                match("<=") -> ComparisonOperator.LTE
                match(">") -> ComparisonOperator.GT
                match("<") -> ComparisonOperator.LT
                match("=") -> ComparisonOperator.EQ
                else -> throw ParseException("Expected operator at position ${index + 1}")
            }

        private fun parseQuotedString(): String {
            if (peek() != '"') {
                throw ParseException("Expected quoted string at position ${index + 1}")
            }

            index++
            val builder = StringBuilder()
            while (!isAtEnd()) {
                val char = text[index++]
                when (char) {
                    '"' -> return builder.toString()
                    '\\' -> {
                        if (isAtEnd()) {
                            throw ParseException("Unterminated escape sequence")
                        }
                        val escaped = text[index++]
                        when (escaped) {
                            '\\', '"' -> builder.append(escaped)
                            else -> {
                                builder.append('\\')
                                builder.append(escaped)
                            }
                        }
                    }

                    else -> builder.append(char)
                }
            }

            throw ParseException("Unterminated string literal")
        }

        private fun parseInteger(): Int {
            if (isAtEnd()) {
                throw ParseException("Expected number at position ${index + 1}")
            }

            val start = index
            if (peek() == '+' || peek() == '-') {
                index++
            }
            val digitsStart = index
            while (!isAtEnd() && peek().isDigit()) {
                index++
            }
            if (digitsStart == index) {
                throw ParseException("Expected number at position ${start + 1}")
            }
            return text.substring(start, index).toIntOrNull()
                ?: throw ParseException("Invalid number at position ${start + 1}")
        }

        private fun parseAndSeparator() {
            val separator = parseIdentifier()
            if (separator != "and") {
                throw ParseException("Expected 'and' at position ${index - separator.length + 1}")
            }
        }

        private fun skipWhitespace() {
            while (!isAtEnd() && peek().isWhitespace()) {
                index++
            }
        }

        private fun match(value: String): Boolean {
            if (!text.startsWith(value, index)) return false
            index += value.length
            return true
        }

        private fun peek(): Char =
            text.getOrElse(index) { '\u0000' }

        private fun isAtEnd(): Boolean =
            index >= text.length
    }

    private sealed interface PathSegment
    private data class FieldSegment(val name: String) : PathSegment
    private data class IndexedSegment(val name: String, val index: Int) : PathSegment
    private class ParseException(message: String) : IllegalArgumentException(message)
}

private fun Char.isIdentifierStart(): Boolean =
    isLetter() || this == '_'

private fun Char.isIdentifierPart(): Boolean =
    isLetterOrDigit() || this == '_'

internal fun String.encodeQuotedQueryValue(): String =
    replace("\\", "\\\\").replace("\"", "\\\"")

internal fun String.toLiteralRegexPattern(): String =
    buildString(length * 2) {
        for (char in this@toLiteralRegexPattern) {
            when (char) {
                '\\', '.', '^', '$', '|', '?', '*', '+', '(', ')', '[', ']', '{', '}' -> {
                    append('\\')
                    append(char)
                }

                else -> append(char)
            }
        }
    }
