package com.liblink.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

// تلوين الأكواد حسب اللغة (Kotlin/Java/XML/JSON/HTML/CSS/Python/Markdown)
object Highlighter {
    private val cKeyword = Color(0xFFC792EA)
    private val cString = Color(0xFFC3E88D)
    private val cNumber = Color(0xFFF78C6C)
    private val cComment = Color(0xFF7F8CAB)
    private val cTag = Color(0xFF22D3EE)
    private val cAttr = Color(0xFFFFCB6B)
    private val cKey = Color(0xFF82AAFF)

    // كل قاعدة = (نمط regex بدون مجموعات التقاط، لون)
    private class Lang(val rules: List<Pair<String, Color>>, flags: Set<RegexOption> = emptySet()) {
        val regex = Regex(rules.joinToString("|") { "(${it.first})" }, flags)
    }

    private const val DQ = "\"(?:\\\\.|[^\"\\\\\\n])*\""
    private const val SQ = "'(?:\\\\.|[^'\\\\\\n])*'"
    private const val TQ = "\"\"\"[\\s\\S]*?\"\"\""
    private const val TQ2 = "'''[\\s\\S]*?'''"
    private const val BLOCK = "/\\*[\\s\\S]*?\\*/"

    private const val CODE_KW = "abstract|as|break|by|catch|class|companion|const|constructor|continue|" +
        "data|do|else|enum|extends|final|finally|for|fun|if|implements|import|in|init|interface|" +
        "internal|is|lateinit|new|null|object|open|override|package|private|protected|public|" +
        "return|sealed|static|super|suspend|this|throw|true|false|try|val|var|void|when|while|" +
        "int|long|float|double|boolean|char|byte|short|String|Boolean|Int|Long|Float|Double|Unit"

    private const val PY_KW = "and|as|assert|break|class|continue|def|del|elif|else|except|finally|for|" +
        "from|global|if|import|in|is|lambda|None|nonlocal|not|or|pass|raise|return|try|while|with|" +
        "yield|True|False|self|print"

    private val cLike = Lang(listOf(
        ("//[^\\n]*|$BLOCK") to cComment,
        ("$TQ|$DQ|$SQ") to cString,
        "@\\w+" to cAttr,
        "\\b\\d+(?:\\.\\d+)?[fFLlxX]?\\b" to cNumber,
        "\\b(?:$CODE_KW)\\b" to cKeyword
    ))

    private val python = Lang(listOf(
        "#[^\\n]*" to cComment,
        "$TQ|$TQ2|$DQ|$SQ" to cString,
        "@\\w+" to cAttr,
        "\\b\\d+(?:\\.\\d+)?\\b" to cNumber,
        "\\b(?:$PY_KW)\\b" to cKeyword
    ))

    private val xml = Lang(listOf(
        "<!--[\\s\\S]*?-->" to cComment,
        "\"[^\"]*\"|'[^']*'" to cString,
        "</?[A-Za-z][\\w:.-]*|/?>" to cTag,
        "[\\w:.-]+(?==)" to cAttr
    ))

    private val json = Lang(listOf(
        "\"(?:\\\\.|[^\"\\\\])*\"(?=\\s*:)" to cKey,
        "\"(?:\\\\.|[^\"\\\\])*\"" to cString,
        "-?\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b" to cNumber,
        "\\b(?:true|false|null)\\b" to cKeyword
    ))

    private val css = Lang(listOf(
        BLOCK to cComment,
        "$DQ|$SQ" to cString,
        "@[\\w-]+" to cKeyword,
        "#[0-9a-fA-F]{3,8}\\b" to cNumber,
        "[\\w-]+(?=\\s*:)" to cAttr,
        "-?\\d*\\.?\\d+(?:px|em|rem|%|vh|vw|pt|ms|s)?" to cNumber,
        "[.#][\\w-]+" to cTag
    ))

    private val markdown = Lang(listOf(
        "```[\\s\\S]*?```|`[^`\\n]+`" to cString,
        "^#{1,6}[^\\n]*" to cTag,
        "\\*\\*[^*\\n]+\\*\\*" to cAttr,
        "\\[[^\\]\\n]+\\]\\([^)\\n]+\\)" to cKey,
        "^[ \\t]*(?:[-*+]|\\d+\\.)(?=\\s)" to cKeyword
    ), setOf(RegexOption.MULTILINE))

    private fun langFor(ext: String): Lang? = when (ext) {
        "kt", "kts", "java", "gradle", "js", "ts", "c", "cpp", "h", "cs", "swift", "go", "php" -> cLike
        "py" -> python
        "xml", "html", "htm", "svg" -> xml
        "json" -> json
        "css" -> css
        "md", "markdown" -> markdown
        else -> null
    }

    fun languageName(ext: String): String = when (ext) {
        "kt", "kts" -> "Kotlin"; "java" -> "Java"; "xml" -> "XML"; "json" -> "JSON"
        "html", "htm" -> "HTML"; "css" -> "CSS"; "py" -> "Python"; "md", "markdown" -> "Markdown"
        "gradle" -> "Gradle"; "js" -> "JavaScript"; "ts" -> "TypeScript"
        else -> if (ext.isEmpty()) "Text" else ext.uppercase()
    }

    // mark: نطاق نتيجة البحث الحالية ليُلوَّن بخلفية
    fun highlight(text: String, ext: String, mark: IntRange? = null): AnnotatedString {
        val lang = langFor(ext)
        return buildAnnotatedString {
            append(text)
            if (lang != null && text.length <= 150_000) {
                for (m in lang.regex.findAll(text)) {
                    val g = (1..lang.rules.size).firstOrNull { m.groups[it] != null } ?: continue
                    addStyle(SpanStyle(color = lang.rules[g - 1].second), m.range.first, m.range.last + 1)
                }
            }
            if (mark != null && mark.first >= 0 && mark.last < text.length)
                addStyle(SpanStyle(background = Color(0x66FFCB6B)), mark.first, mark.last + 1)
        }
    }
}
