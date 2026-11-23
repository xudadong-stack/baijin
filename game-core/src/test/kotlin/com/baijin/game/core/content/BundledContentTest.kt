package com.baijin.game.core.content

import org.junit.Assert.fail
import org.junit.Test

class BundledContentTest {
    @Test
    fun validatesTheSamplePackagedByApp() {
        when (val result = ContentLoader.load(readBundledSample())) {
            is ContentLoadResult.Loaded -> println("content/sample.json v${result.content.contentVersion} 校验通过")
            is ContentLoadResult.Invalid -> fail(result.issues.joinToString("\n") {
                "content/sample.json ${it.path}：${it.message}"
            })
        }
    }
}

internal fun readBundledSample(): String =
    requireNotNull(BundledContentTest::class.java.getResourceAsStream("/content/sample.json")) {
        "缺少 app 随包内容 content/sample.json"
    }.bufferedReader(Charsets.UTF_8).use { it.readText() }

internal fun loadBundledSample(): GameContent =
    (ContentLoader.load(readBundledSample()) as ContentLoadResult.Loaded).content
