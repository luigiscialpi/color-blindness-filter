package com.luigiscialpi.colorblindnessfilter.data

import com.luigiscialpi.colorblindnessfilter.measure.ChromaticAxis
import java.util.Locale

/**
 * Serializzatore e deserializzatore JSON leggero in puro Kotlin per [TestSessionSnapshot],
 * privo di dipendenze esterne o stub di Android, eseguibile direttamente nei test di unità.
 */
object TestSessionJson {

    fun toJson(snapshot: TestSessionSnapshot): String = buildString {
        append("{\n")
        append("  \"id\": ").append(escape(snapshot.id)).append(",\n")
        append("  \"type\": ").append(escape(snapshot.type.name)).append(",\n")
        append("  \"title\": ").append(escape(snapshot.title)).append(",\n")
        append("  \"startedAtEpochMs\": ").append(snapshot.startedAtEpochMs).append(",\n")
        append("  \"completedAtEpochMs\": ").append(snapshot.completedAtEpochMs?.toString() ?: "null").append(",\n")
        append("  \"isCompleted\": ").append(snapshot.isCompleted).append(",\n")
        append("  \"completedBlocks\": [\n")
        snapshot.completedBlocks.forEachIndexed { i, block ->
            append("    {\n")
            append("      \"label\": ").append(escape(block.label)).append(",\n")
            append("      \"displayLabel\": ").append(escape(block.displayLabel)).append(",\n")
            append("      \"beta\": ").append(String.format(Locale.ROOT, "%.6f", block.beta)).append(",\n")
            append("      \"isSweep\": ").append(block.isSweep).append(",\n")
            append("      \"thresholdM\": ").append(block.thresholds[ChromaticAxis.M_ISOLATING]?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "null").append(",\n")
            append("      \"thresholdS\": ").append(block.thresholds[ChromaticAxis.S_ISOLATING]?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "null").append(",\n")
            append("      \"catchAccuracy\": ").append(block.catchAccuracy?.let { String.format(Locale.ROOT, "%.6f", it) } ?: "null").append(",\n")
            append("      \"csv\": ").append(escape(block.csv)).append("\n")
            append("    }").append(if (i < snapshot.completedBlocks.lastIndex) ",\n" else "\n")
        }
        append("  ],\n")
        append("  \"remainingBlocks\": [\n")
        snapshot.remainingBlocks.forEachIndexed { i, block ->
            append("    {\n")
            append("      \"label\": ").append(escape(block.label)).append(",\n")
            append("      \"displayLabel\": ").append(escape(block.displayLabel)).append(",\n")
            append("      \"beta\": ").append(String.format(Locale.ROOT, "%.6f", block.beta)).append(",\n")
            append("      \"axes\": [").append(block.axes.joinToString(", ") { escape(it.name) }).append("],\n")
            append("      \"isSweep\": ").append(block.isSweep).append("\n")
            append("    }").append(if (i < snapshot.remainingBlocks.lastIndex) ",\n" else "\n")
        }
        append("  ]\n")
        append("}\n")
    }

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String): TestSessionSnapshot {
        val map = JsonReader(json).parseValue() as? Map<String, Any?>
            ?: error("JSON non valido: la radice deve essere un oggetto")
        val id = map["id"] as String
        val type = TestSuiteType.valueOf(map["type"] as String)
        val title = map["title"] as String
        val startedAt = (map["startedAtEpochMs"] as Number).toLong()
        val completedAt = (map["completedAtEpochMs"] as? Number)?.toLong()
        val isCompleted = (map["isCompleted"] as? Boolean) ?: false
        val completedBlocksRaw = (map["completedBlocks"] as? List<Map<String, Any?>>) ?: emptyList()
        val completedBlocks = completedBlocksRaw.map { b ->
            val m = (b["thresholdM"] as? Number)?.toDouble()
            val s = (b["thresholdS"] as? Number)?.toDouble()
            val thresholds = buildMap {
                if (m != null) put(ChromaticAxis.M_ISOLATING, m)
                if (s != null) put(ChromaticAxis.S_ISOLATING, s)
            }
            SavedBlockResult(
                label = b["label"] as String,
                displayLabel = (b["displayLabel"] as? String) ?: (b["label"] as String),
                beta = (b["beta"] as Number).toDouble(),
                isSweep = (b["isSweep"] as? Boolean) ?: false,
                thresholds = thresholds,
                catchAccuracy = (b["catchAccuracy"] as? Number)?.toDouble(),
                csv = (b["csv"] as? String) ?: "",
            )
        }
        val remainingBlocksRaw = (map["remainingBlocks"] as? List<Map<String, Any?>>) ?: emptyList()
        val remainingBlocks = remainingBlocksRaw.map { b ->
            val axesRaw = (b["axes"] as? List<String>) ?: listOf("M_ISOLATING")
            SavedPendingBlock(
                label = b["label"] as String,
                displayLabel = (b["displayLabel"] as? String) ?: (b["label"] as String),
                beta = (b["beta"] as Number).toDouble(),
                axes = axesRaw.map { ChromaticAxis.valueOf(it) },
                isSweep = (b["isSweep"] as? Boolean) ?: false,
            )
        }
        return TestSessionSnapshot(
            id = id,
            type = type,
            title = title,
            startedAtEpochMs = startedAt,
            completedAtEpochMs = completedAt,
            isCompleted = isCompleted,
            completedBlocks = completedBlocks,
            remainingBlocks = remainingBlocks,
        )
    }

    private fun escape(str: String): String = buildString {
        append('"')
        for (ch in str) {
            when (ch) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> {
                    if (ch.code < 0x20) {
                        append(String.format(Locale.ROOT, "\\u%04x", ch.code))
                    } else {
                        append(ch)
                    }
                }
            }
        }
        append('"')
    }

    private class JsonReader(private val src: String) {
        private var pos = 0

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) pos++
        }

        private fun peek(): Char? {
            skipWhitespace()
            return if (pos < src.length) src[pos] else null
        }

        private fun consume(): Char {
            skipWhitespace()
            check(pos < src.length) { "Fine inattesa dell'input JSON" }
            return src[pos++]
        }

        fun parseValue(): Any? {
            val ch = peek() ?: error("Valore JSON inatteso alla fine dell'input")
            return when (ch) {
                '{' -> parseObject()
                '[' -> parseArray()
                '"' -> parseString()
                't', 'f' -> parseBoolean()
                'n' -> parseNull()
                else -> parseNumber()
            }
        }

        private fun parseObject(): Map<String, Any?> {
            check(consume() == '{')
            val map = mutableMapOf<String, Any?>()
            if (peek() == '}') {
                consume()
                return map
            }
            while (true) {
                val key = parseString()
                val colon = consume()
                check(colon == ':') { "Previsto ':' dopo la chiave '$key', trovato '$colon'" }
                val value = parseValue()
                map[key] = value
                val next = consume()
                if (next == '}') break
                check(next == ',') { "Previsto ',' o '}', trovato '$next'" }
            }
            return map
        }

        private fun parseArray(): List<Any?> {
            check(consume() == '[')
            val list = mutableListOf<Any?>()
            if (peek() == ']') {
                consume()
                return list
            }
            while (true) {
                list.add(parseValue())
                val next = consume()
                if (next == ']') break
                check(next == ',') { "Previsto ',' o ']', trovato '$next'" }
            }
            return list
        }

        private fun parseString(): String {
            check(consume() == '"')
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                if (ch == '"') return sb.toString()
                if (ch == '\\') {
                    check(pos < src.length) { "Sequenza di escape non terminata" }
                    val esc = src[pos++]
                    when (esc) {
                        '"' -> sb.append('"')
                        '\\' -> sb.append('\\')
                        '/' -> sb.append('/')
                        'b' -> sb.append('\b')
                        'f' -> sb.append('\u000C')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        't' -> sb.append('\t')
                        'u' -> {
                            check(pos + 4 <= src.length) { "Sequenza unicode incompleta" }
                            val hex = src.substring(pos, pos + 4)
                            pos += 4
                            sb.append(hex.toInt(16).toChar())
                        }
                        else -> sb.append(esc)
                    }
                } else {
                    sb.append(ch)
                }
            }
            error("Stringa JSON non terminata")
        }

        private fun parseBoolean(): Boolean {
            skipWhitespace()
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                error("Previsto booleano alla posizione $pos")
            }
        }

        private fun parseNull(): Nothing? {
            skipWhitespace()
            check(src.startsWith("null", pos)) { "Previsto null alla posizione $pos" }
            pos += 4
            return null
        }

        private fun parseNumber(): Double {
            skipWhitespace()
            val start = pos
            if (pos < src.length && (src[pos] == '-' || src[pos] == '+')) pos++
            while (pos < src.length && (src[pos].isDigit() || src[pos] == '.' || src[pos] == 'e' || src[pos] == 'E' || src[pos] == '-' || src[pos] == '+')) {
                pos++
            }
            val str = src.substring(start, pos)
            return str.toDoubleOrNull() ?: error("Numero non valido: $str")
        }
    }
}
