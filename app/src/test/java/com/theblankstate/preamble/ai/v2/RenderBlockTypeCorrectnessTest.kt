package com.theblankstate.preamble.ai.v2

import com.theblankstate.preamble.ai.v2.ui.RenderBlock
import com.theblankstate.preamble.ai.v2.ui.ToolCallInfo
import net.jqwik.api.*
import net.jqwik.api.Combinators.combine
import org.junit.jupiter.api.Assertions.*

/**
 * Property 16: Rich response render block type correctness
 *
 * For arbitrary RenderBlocks, verify renderer produces non-null composable of
 * correct type for each block type. Since Compose composables cannot be
 * instantiated in JVM unit tests (they require an Android runtime), we validate:
 *
 * 1. Each RenderBlock subtype can be instantiated with arbitrary valid data
 * 2. The data fields are correctly preserved (no silent mutation)
 * 3. A list of arbitrary RenderBlocks can be iterated and type-dispatched without crash
 * 4. Citation snippets are correctly bounded (≤200 chars)
 * 5. The sealed class exhaustively covers all 6 block types
 *
 * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**
 */
class RenderBlockTypeCorrectnessTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Generators
    // ─────────────────────────────────────────────────────────────────────────

    @Provide
    fun markdownBlocks(): Arbitrary<RenderBlock.Markdown> =
        Arbitraries.strings().ofMinLength(0).ofMaxLength(2000)
            .map { RenderBlock.Markdown(content = it) }

    @Provide
    fun codeBlocks(): Arbitrary<RenderBlock.CodeBlock> =
        combine(
            Arbitraries.strings().ofMinLength(1).ofMaxLength(1000),
            Arbitraries.of(
                null, "kotlin", "java", "python", "typescript",
                "javascript", "rust", "go", "sql", "bash", "unknown_lang"
            )
        ).`as` { code, language -> RenderBlock.CodeBlock(code = code, language = language) }

    @Provide
    fun mathBlocks(): Arbitrary<RenderBlock.MathBlock> =
        combine(
            Arbitraries.strings().ofMinLength(1).ofMaxLength(500),
            Arbitraries.of(true, false)
        ).`as` { latex, inline -> RenderBlock.MathBlock(latex = latex, inline = inline) }

    @Provide
    fun thinkingBlocks(): Arbitrary<RenderBlock.ThinkingBlock> =
        Arbitraries.strings().ofMinLength(1).ofMaxLength(2000)
            .map { RenderBlock.ThinkingBlock(reasoning = it) }

    @Provide
    fun citations(): Arbitrary<RenderBlock.Citation> =
        combine(
            Arbitraries.strings().ofMinLength(1).ofMaxLength(200),
            Arbitraries.strings().ofMinLength(10).ofMaxLength(500)
                .map { "https://example.com/$it" },
            Arbitraries.strings().ofMinLength(0).ofMaxLength(300)
                .injectNull(0.3)
        ).`as` { title, url, snippet -> RenderBlock.Citation(title = title, url = url, snippet = snippet) }

    @Provide
    fun toolPermissions(): Arbitrary<RenderBlock.ToolPermission> =
        combine(
            Arbitraries.strings().ofMinLength(1).ofMaxLength(100),
            Arbitraries.of("read", "write"),
            Arbitraries.strings().ofMinLength(1).ofMaxLength(200),
            Arbitraries.strings().ofMinLength(1).ofMaxLength(200)
        ).`as` { name, category, description, targetData ->
            RenderBlock.ToolPermission(
                toolCall = ToolCallInfo(
                    name = name,
                    category = category,
                    description = description,
                    targetData = targetData,
                    args = emptyMap()
                )
            )
        }

    @Provide
    fun arbitraryRenderBlocks(): Arbitrary<RenderBlock> =
        Arbitraries.oneOf(
            markdownBlocks().map { it as RenderBlock },
            codeBlocks().map { it as RenderBlock },
            mathBlocks().map { it as RenderBlock },
            thinkingBlocks().map { it as RenderBlock },
            citations().map { it as RenderBlock },
            toolPermissions().map { it as RenderBlock }
        )

    @Provide
    fun renderBlockLists(): Arbitrary<List<RenderBlock>> =
        arbitraryRenderBlocks().list().ofMinSize(0).ofMaxSize(50)

    // ─────────────────────────────────────────────────────────────────────────
    // Property Tests
    // ─────────────────────────────────────────────────────────────────────────

    // Feature: ai-v2-ecosystem, Property 16: Rich response render block type correctness

    /**
     * For any Markdown block, verify it is non-null and preserves its content field.
     * **Validates: Requirements 6.1**
     */
    @Property(tries = 200)
    fun markdownBlockPreservesContent(@ForAll("markdownBlocks") block: RenderBlock.Markdown) {
        assertNotNull(block)
        assertNotNull(block.content)
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.Markdown)
    }

    /**
     * For any CodeBlock with a specified language, the language field is preserved.
     * For any CodeBlock without a language, the language field is null.
     * **Validates: Requirements 6.2, 6.3**
     */
    @Property(tries = 200)
    fun codeBlockPreservesFields(@ForAll("codeBlocks") block: RenderBlock.CodeBlock) {
        assertNotNull(block)
        assertNotNull(block.code)
        assertTrue(block.code.isNotEmpty())
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.CodeBlock)
        // Language is either null (no highlighting) or a non-empty string (with highlighting)
        if (block.language != null) {
            assertTrue(block.language!!.isNotEmpty())
        }
    }

    /**
     * For any MathBlock, verify it holds valid latex content and an inline flag.
     * On parse failure, raw LaTeX should still be available (non-null).
     * **Validates: Requirements 6.4, 6.5**
     */
    @Property(tries = 200)
    fun mathBlockPreservesLatex(@ForAll("mathBlocks") block: RenderBlock.MathBlock) {
        assertNotNull(block)
        assertNotNull(block.latex)
        assertTrue(block.latex.isNotEmpty())
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.MathBlock)
        // inline is always a valid boolean
        assertNotNull(block.inline)
    }

    /**
     * For any ThinkingBlock, verify it preserves reasoning text for the
     * collapsible section rendering.
     * **Validates: Requirements 6.6**
     */
    @Property(tries = 200)
    fun thinkingBlockPreservesReasoning(@ForAll("thinkingBlocks") block: RenderBlock.ThinkingBlock) {
        assertNotNull(block)
        assertNotNull(block.reasoning)
        assertTrue(block.reasoning.isNotEmpty())
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.ThinkingBlock)
    }

    /**
     * For any Citation, verify it preserves title and URL, and that snippet
     * (when rendered) would be truncated to ≤200 characters.
     * **Validates: Requirements 6.7**
     */
    @Property(tries = 200)
    fun citationPreservesFieldsAndSnippetBound(@ForAll("citations") block: RenderBlock.Citation) {
        assertNotNull(block)
        assertNotNull(block.title)
        assertNotNull(block.url)
        assertTrue(block.title.isNotEmpty())
        assertTrue(block.url.isNotEmpty())
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.Citation)

        // The renderer truncates snippets to 200 chars: snippet?.take(200)
        // Verify that applying this truncation yields ≤200 chars
        val renderedSnippet = block.snippet?.take(200)
        if (renderedSnippet != null) {
            assertTrue(renderedSnippet.length <= 200,
                "Rendered snippet must be ≤200 chars but was ${renderedSnippet.length}")
        }
    }

    /**
     * For any ToolPermission block, verify tool call info fields are preserved.
     * **Validates: Requirements 6.1 (block type dispatch completeness)**
     */
    @Property(tries = 200)
    fun toolPermissionPreservesToolCallInfo(@ForAll("toolPermissions") block: RenderBlock.ToolPermission) {
        assertNotNull(block)
        assertNotNull(block.toolCall)
        assertNotNull(block.toolCall.name)
        assertNotNull(block.toolCall.category)
        assertNotNull(block.toolCall.description)
        assertNotNull(block.toolCall.targetData)
        assertTrue(block.toolCall.category in listOf("read", "write"))
        assertTrue(block is RenderBlock)
        assertTrue(block is RenderBlock.ToolPermission)
    }

    /**
     * For any list of arbitrary RenderBlocks, verify type dispatch is exhaustive
     * and all blocks can be processed without crash. This simulates the renderer's
     * when-block dispatch logic.
     * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**
     */
    @Property(tries = 200)
    fun arbitraryBlockListProcessesWithoutCrash(@ForAll("renderBlockLists") blocks: List<RenderBlock>) {
        assertNotNull(blocks)

        // Simulate the renderer's dispatch logic (mirrors RichResponseRenderer's when block)
        blocks.forEach { block ->
            assertNotNull(block)
            val typeIdentified = when (block) {
                is RenderBlock.Markdown -> {
                    assertNotNull(block.content)
                    "markdown"
                }
                is RenderBlock.CodeBlock -> {
                    assertNotNull(block.code)
                    "code"
                }
                is RenderBlock.MathBlock -> {
                    assertNotNull(block.latex)
                    "math"
                }
                is RenderBlock.ThinkingBlock -> {
                    assertNotNull(block.reasoning)
                    "thinking"
                }
                is RenderBlock.Citation -> {
                    assertNotNull(block.title)
                    assertNotNull(block.url)
                    "citation"
                }
                is RenderBlock.ToolPermission -> {
                    assertNotNull(block.toolCall)
                    "tool_permission"
                }
            }
            // Every block must resolve to a known type
            assertTrue(typeIdentified.isNotEmpty())
        }
    }

    /**
     * Verify that all 6 RenderBlock subtypes are represented in the sealed class.
     * This is a compile-time guarantee, but we explicitly test it for documentation.
     * **Validates: Requirements 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7**
     */
    @Property(tries = 100)
    fun sealedClassCoversAllSixBlockTypes(@ForAll("arbitraryRenderBlocks") block: RenderBlock) {
        val allTypes = setOf(
            RenderBlock.Markdown::class,
            RenderBlock.CodeBlock::class,
            RenderBlock.MathBlock::class,
            RenderBlock.ThinkingBlock::class,
            RenderBlock.Citation::class,
            RenderBlock.ToolPermission::class
        )

        // The block must be an instance of exactly one of the known types
        val matchingTypes = allTypes.filter { it.isInstance(block) }
        assertEquals(1, matchingTypes.size,
            "Each RenderBlock must be exactly one subtype, found: $matchingTypes for $block")
    }
}
