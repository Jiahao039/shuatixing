package com.shutixing.app.parser

import com.shutixing.app.parser.model.TextLine

object Normalizer {
    private val spaces = Regex("""\s+""")
    private val pageNumber = Regex("""^\s*(?:[-—–]\s*)?\d{1,4}\s*(?:[-—–])?\s*$""")
    private val pageNumberCn = Regex("""^\s*第\s*\d+\s*页(?:\s*[/／]\s*共?\s*\d+\s*页)?\s*$""")

    fun normalize(lines: List<TextLine>): List<TextLine> {
        val cleaned = lines
            .map { it.copy(text = normalizeText(it.text)) }
            .filter { it.text.isNotEmpty() }

        val pageCount = cleaned.map { it.page }.distinct().size
        val repeatedKeys = if (pageCount >= 2) {
            cleaned.groupBy { headerKey(it) }
                .filterValues { group -> group.map { it.page }.distinct().size > pageCount / 2.0 }
                .keys
        } else {
            emptySet()
        }

        return cleaned.filterNot { line ->
            headerKey(line) in repeatedKeys ||
                pageNumber.matches(line.text) ||
                pageNumberCn.matches(line.text)
        }
    }

    private fun headerKey(line: TextLine): String = "${line.text}@${(line.top / 6f).toInt()}"

    private fun normalizeText(raw: String): String {
        val converted = buildString(raw.length) {
            for (ch in raw) {
                append(
                    when (ch) {
                        in '０'..'９' -> '0' + (ch - '０')
                        in 'Ａ'..'Ｚ' -> 'A' + (ch - 'Ａ')
                        in 'ａ'..'ｚ' -> 'a' + (ch - 'ａ')
                        '　' -> ' '
                        '．' -> '.'
                        else -> ch
                    },
                )
            }
        }
        return converted.replace(spaces, " ").trim()
    }
}
