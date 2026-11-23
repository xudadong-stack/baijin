package com.baijin.game.core.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

data class ContentIssue(val path: String, val message: String)

sealed interface ContentLoadResult {
    data class Loaded(val content: GameContent) : ContentLoadResult
    data class Invalid(val issues: List<ContentIssue>) : ContentLoadResult
}

object ContentLoader {
    private val decoderPath = Regex("""path: (\$[.\w\[\]]*)""")

    fun load(json: String): ContentLoadResult {
        val content = try {
            Json.decodeFromString<GameContent>(json)
        } catch (error: SerializationException) {
            return ContentLoadResult.Invalid(decodingIssues(error))
        }

        val issues = ContentValidator().validate(content)
        return if (issues.isEmpty()) ContentLoadResult.Loaded(content) else ContentLoadResult.Invalid(issues)
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun decodingIssues(error: SerializationException): List<ContentIssue> {
        val detail = error.message.orEmpty().lineSequence().first().trim()
        val path = decoderPath.find(detail)?.groupValues?.get(1) ?: "$"
        if (error is MissingFieldException) {
            return error.missingFields.map { field -> ContentIssue("$path.$field", "缺少字段") }
        }
        return listOf(ContentIssue(path, detail.ifEmpty { "JSON 格式无效" }))
    }
}
