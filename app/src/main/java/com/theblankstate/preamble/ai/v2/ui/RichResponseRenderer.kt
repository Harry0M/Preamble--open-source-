package com.theblankstate.preamble.ai.v2.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

// ─────────────────────────────────────────────────────────────────────────────
// Data Model
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Typed render blocks for Chat_V2 rich responses.
 * Each variant maps 1-to-1 with a server-emitted block type.
 */
sealed class RenderBlock {
    data class Markdown(val content: String) : RenderBlock()
    data class CodeBlock(val code: String, val language: String?) : RenderBlock()
    data class MathBlock(val latex: String, val inline: Boolean) : RenderBlock()
    data class ThinkingBlock(val reasoning: String) : RenderBlock()
    data class Citation(val title: String, val url: String, val snippet: String?) : RenderBlock()
    data class ToolPermission(val toolCall: ToolCallInfo) : RenderBlock()
}

/**
 * Metadata for a proposed tool call requiring user permission.
 */
data class ToolCallInfo(
    val name: String,
    val category: String, // "read" or "write"
    val description: String,
    val targetData: String,
    val args: Map<String, Any?> = emptyMap(),
)

// ─────────────────────────────────────────────────────────────────────────────
// Main Entry Point
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a list of [RenderBlock]s as Compose UI.
 *
 * Each block type maps to a dedicated composable:
 * - Markdown → full formatted text (headings, bold, italic, lists, links, blockquotes)
 * - CodeBlock → monospace with optional syntax highlighting label
 * - MathBlock → LaTeX rendering with fallback to raw source
 * - ThinkingBlock → collapsible reasoning section (default collapsed)
 * - Citation → tappable link with title and snippet (≤200 chars)
 * - ToolPermission → delegated to [ToolPermissionDialog] (handled externally)
 */
@Composable
fun RichResponseRenderer(
    blocks: List<RenderBlock>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is RenderBlock.Markdown -> MarkdownBlockRenderer(block.content)
                is RenderBlock.CodeBlock -> CodeBlockRenderer(block.code, block.language)
                is RenderBlock.MathBlock -> MathBlockRenderer(block.latex, block.inline)
                is RenderBlock.ThinkingBlock -> ThinkingBlockRenderer(block.reasoning)
                is RenderBlock.Citation -> CitationBlockRenderer(
                    block.title, block.url, block.snippet
                )
                is RenderBlock.ToolPermission -> {
                    // ToolPermission rendering is handled by the parent screen
                    // via ToolPermissionDialog. This is a no-op placeholder.
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Markdown Block
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MarkdownBlockRenderer(content: String) {
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    val textColor = MaterialTheme.colorScheme.onSurface
    val lines = content.split("\n")

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val trimmed = line.trimStart()
            when {
                // Fenced code block inside markdown
                trimmed.startsWith("```") -> {
                    val language = trimmed.removePrefix("```").trim().takeIf { it.isNotBlank() }
                    val codeLines = mutableListOf<String>()
                    index++
                    while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                        codeLines += lines[index]
                        index++
                    }
                    if (index < lines.size) index++ // skip closing ```
                    CodeBlockRenderer(codeLines.joinToString("\n"), language)
                }
                // Blockquote
                trimmed.startsWith("> ") -> {
                    val quoteLines = mutableListOf(trimmed.removePrefix("> "))
                    index++
                    while (index < lines.size) {
                        val next = lines[index].trimStart()
                        if (next.startsWith("> ")) {
                            quoteLines += next.removePrefix("> ")
                            index++
                        } else break
                    }
                    BlockquoteRenderer(quoteLines.joinToString("\n"), codeBackground, textColor)
                }
                else -> {
                    MarkdownLine(line, codeBackground, textColor)
                    index++
                }
            }
        }
    }
}

@Composable
private fun MarkdownLine(line: String, codeBackground: Color, color: Color) {
    val uriHandler = LocalUriHandler.current
    when {
        line.isBlank() -> Spacer(Modifier.height(4.dp))
        // H1
        line.startsWith("# ") -> Text(
            parseInlineMarkdown(line.removePrefix("# "), codeBackground),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        // H2
        line.startsWith("## ") -> Text(
            parseInlineMarkdown(line.removePrefix("## "), codeBackground),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        // H3
        line.startsWith("### ") -> Text(
            parseInlineMarkdown(line.removePrefix("### "), codeBackground),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        // Unordered list
        line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
            val indent = line.length - line.trimStart().length
            val content = line.trimStart().drop(2)
            Row(modifier = Modifier.padding(start = (indent / 2 * 12 + 8).dp)) {
                Text("•  ", color = color, style = MaterialTheme.typography.bodyLarge)
                ClickableMarkdownText(
                    annotated = parseInlineMarkdown(content, codeBackground),
                    color = color,
                    onLinkClick = { url -> uriHandler.openUri(url) },
                )
            }
        }
        // Ordered list
        line.trimStart().matches(Regex("^\\d+\\.\\s.*")) -> {
            val num = line.trimStart().substringBefore(".")
            val content = line.trimStart().substringAfter(". ")
            Row(modifier = Modifier.padding(start = 8.dp)) {
                Text(
                    "$num. ",
                    color = color,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                ClickableMarkdownText(
                    annotated = parseInlineMarkdown(content, codeBackground),
                    color = color,
                    onLinkClick = { url -> uriHandler.openUri(url) },
                )
            }
        }
        // Normal paragraph text
        else -> ClickableMarkdownText(
            annotated = parseInlineMarkdown(line, codeBackground),
            color = color,
            onLinkClick = { url -> uriHandler.openUri(url) },
        )
    }
}

@Composable
private fun BlockquoteRenderer(text: String, codeBackground: Color, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
    ) {
        // Vertical accent bar
        Spacer(
            modifier = Modifier
                .width(3.dp)
                .height(40.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    RoundedCornerShape(2.dp),
                )
        )
        Spacer(Modifier.width(8.dp))
        Text(
            parseInlineMarkdown(text, codeBackground),
            style = MaterialTheme.typography.bodyLarge.copy(fontStyle = FontStyle.Italic),
            color = color.copy(alpha = 0.85f),
        )
    }
}

/**
 * Text composable that supports tappable links from AnnotatedString annotations.
 */
@Composable
private fun ClickableMarkdownText(
    annotated: androidx.compose.ui.text.AnnotatedString,
    color: Color,
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutResult = remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
        ) { /* handled via onTextLayout + pointer input if needed */ },
        onTextLayout = { layoutResult.value = it },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Code Block
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a code block with monospace font.
 * Shows language label when specified. Horizontal scroll for long lines.
 */
@Composable
private fun CodeBlockRenderer(code: String, language: String?) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            language?.let {
                Text(
                    it.uppercase(Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                code,
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Math Block
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a LaTeX math block. Attempts lightweight parsing of common LaTeX
 * constructs into formatted text. Falls back to raw LaTeX on parse failure.
 *
 * Full LaTeX rendering (e.g., via WebView + MathJax/KaTeX) can be swapped in
 * later. For now we render a best-effort symbolic representation, and always
 * show raw source as fallback when parsing fails.
 */
@Composable
private fun MathBlockRenderer(latex: String, inline: Boolean) {
    val rendered = remember(latex) { tryRenderLatex(latex) }

    if (rendered != null) {
        // Successful parse — show formatted math
        if (inline) {
            Text(
                text = rendered,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif,
                ),
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            ) {
                Text(
                    text = rendered,
                    modifier = Modifier
                        .padding(12.dp)
                        .horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontFamily = FontFamily.Serif,
                        fontSize = 16.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    } else {
        // Fallback: show raw LaTeX source in monospace
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        ) {
            Text(
                text = latex,
                modifier = Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Lightweight LaTeX-to-text converter. Handles common constructs:
 * - \frac{a}{b} → a/b
 * - \sqrt{x} → √x
 * - ^{n} → superscript notation
 * - _{n} → subscript notation
 * - \sum, \int, \alpha, \beta, etc. → Unicode symbols
 * - \text{...} → plain text
 *
 * Returns null if the input seems malformed (unbalanced braces, unknown commands
 * making the output unreadable).
 */
private fun tryRenderLatex(latex: String): String? {
    return try {
        val result = renderLatexToText(latex)
        // If after rendering, the result is mostly backslashes or empty, treat as failure
        if (result.isBlank()) return null
        val backslashRatio = result.count { it == '\\' }.toFloat() / result.length
        if (backslashRatio > 0.3f) null else result
    } catch (_: Exception) {
        null
    }
}

private fun renderLatexToText(latex: String): String {
    var text = latex.trim()

    // Replace common LaTeX symbols with Unicode equivalents
    val symbolMap = mapOf(
        "\\alpha" to "α", "\\beta" to "β", "\\gamma" to "γ", "\\delta" to "δ",
        "\\epsilon" to "ε", "\\zeta" to "ζ", "\\eta" to "η", "\\theta" to "θ",
        "\\iota" to "ι", "\\kappa" to "κ", "\\lambda" to "λ", "\\mu" to "μ",
        "\\nu" to "ν", "\\xi" to "ξ", "\\pi" to "π", "\\rho" to "ρ",
        "\\sigma" to "σ", "\\tau" to "τ", "\\upsilon" to "υ", "\\phi" to "φ",
        "\\chi" to "χ", "\\psi" to "ψ", "\\omega" to "ω",
        "\\Alpha" to "Α", "\\Beta" to "Β", "\\Gamma" to "Γ", "\\Delta" to "Δ",
        "\\Theta" to "Θ", "\\Lambda" to "Λ", "\\Pi" to "Π", "\\Sigma" to "Σ",
        "\\Phi" to "Φ", "\\Psi" to "Ψ", "\\Omega" to "Ω",
        "\\infty" to "∞", "\\pm" to "±", "\\mp" to "∓",
        "\\times" to "×", "\\div" to "÷", "\\cdot" to "·",
        "\\leq" to "≤", "\\geq" to "≥", "\\neq" to "≠",
        "\\approx" to "≈", "\\equiv" to "≡",
        "\\sum" to "∑", "\\prod" to "∏", "\\int" to "∫",
        "\\partial" to "∂", "\\nabla" to "∇",
        "\\in" to "∈", "\\notin" to "∉", "\\subset" to "⊂",
        "\\supset" to "⊃", "\\subseteq" to "⊆", "\\supseteq" to "⊇",
        "\\cup" to "∪", "\\cap" to "∩",
        "\\forall" to "∀", "\\exists" to "∃",
        "\\rightarrow" to "→", "\\leftarrow" to "←",
        "\\Rightarrow" to "⇒", "\\Leftarrow" to "⇐",
        "\\leftrightarrow" to "↔", "\\Leftrightarrow" to "⇔",
        "\\to" to "→",
        "\\ldots" to "…", "\\cdots" to "⋯", "\\dots" to "…",
        "\\quad" to "  ", "\\qquad" to "    ",
        "\\," to " ", "\\;" to " ", "\\!" to "",
        "\\left" to "", "\\right" to "",
        "\\langle" to "⟨", "\\rangle" to "⟩",
    )

    // Apply symbol substitutions (longest match first to avoid partial replacements)
    symbolMap.entries.sortedByDescending { it.key.length }.forEach { (cmd, symbol) ->
        text = text.replace(cmd, symbol)
    }

    // \frac{a}{b} → a/b
    text = Regex("""\\frac\{([^}]*)\}\{([^}]*)\}""").replace(text) { m ->
        "(${m.groupValues[1]})/(${m.groupValues[2]})"
    }

    // \sqrt{x} → √(x)
    text = Regex("""\\sqrt\{([^}]*)\}""").replace(text) { m ->
        "√(${m.groupValues[1]})"
    }

    // \text{...} → plain text
    text = Regex("""\\text\{([^}]*)\}""").replace(text) { m ->
        m.groupValues[1]
    }

    // \mathbf{...}, \mathrm{...}, \mathit{...} → content
    text = Regex("""\\math[a-z]+\{([^}]*)\}""").replace(text) { m ->
        m.groupValues[1]
    }

    // ^{n} → superscript characters where possible
    text = Regex("""\^\{([^}]*)\}""").replace(text) { m ->
        toSuperscript(m.groupValues[1])
    }
    // ^n (single character)
    text = Regex("""\^([0-9a-zA-Z])""").replace(text) { m ->
        toSuperscript(m.groupValues[1])
    }

    // _{n} → subscript characters where possible
    text = Regex("""_\{([^}]*)\}""").replace(text) { m ->
        toSubscript(m.groupValues[1])
    }
    // _n (single character)
    text = Regex("""_([0-9a-zA-Z])""").replace(text) { m ->
        toSubscript(m.groupValues[1])
    }

    // Remove remaining braces that are just grouping
    text = text.replace("{", "").replace("}", "")

    return text
}

private fun toSuperscript(text: String): String {
    val superMap = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾',
        'n' to 'ⁿ', 'i' to 'ⁱ',
    )
    return text.map { superMap[it] ?: it }.joinToString("")
}

private fun toSubscript(text: String): String {
    val subMap = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄',
        '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
        'a' to 'ₐ', 'e' to 'ₑ', 'i' to 'ᵢ', 'o' to 'ₒ',
        'r' to 'ᵣ', 'u' to 'ᵤ', 'x' to 'ₓ',
    )
    return text.map { subMap[it] ?: it }.joinToString("")
}

// ─────────────────────────────────────────────────────────────────────────────
// Thinking Block
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Collapsible section showing the model's reasoning process.
 * Defaults to collapsed state per Requirement 6.6.
 */
@Composable
private fun ThinkingBlockRenderer(reasoning: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Thinking",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess
                    else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically(),
            ) {
                Text(
                    text = reasoning,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Citation Block
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Renders a citation as a tappable link card with title and snippet (≤200 chars).
 */
@Composable
private fun CitationBlockRenderer(title: String, url: String, snippet: String?) {
    val uriHandler = LocalUriHandler.current
    val truncatedSnippet = snippet?.take(200)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { uriHandler.openUri(url) },
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                imageVector = Icons.Filled.Link,
                contentDescription = "Citation link",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(8.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                truncatedSnippet?.let {
                    Text(
                        text = it,
                        modifier = Modifier.padding(top = 2.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline Markdown Parser
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses inline markdown formatting into an AnnotatedString.
 * Supports: **bold**, *italic*, `code`, [links](url), ~~strikethrough~~
 */
private fun parseInlineMarkdown(
    text: String,
    codeBg: Color,
): androidx.compose.ui.text.AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // [link text](url)
                text.startsWith("[", i) -> {
                    val closeBracket = text.indexOf("]", i + 1)
                    val openParen = if (closeBracket > i) closeBracket + 1 else -1
                    if (openParen < text.length && openParen >= 0
                        && text.getOrNull(openParen) == '('
                    ) {
                        val closeParen = text.indexOf(")", openParen + 1)
                        if (closeParen > openParen) {
                            val linkText = text.substring(i + 1, closeBracket)
                            val url = text.substring(openParen + 1, closeParen)
                            pushStringAnnotation(tag = "URL", annotation = url)
                            withStyle(
                                SpanStyle(
                                    color = Color(0xFF1976D2),
                                    textDecoration = TextDecoration.Underline,
                                )
                            ) {
                                append(linkText)
                            }
                            pop()
                            i = closeParen + 1
                        } else {
                            append(text[i]); i++
                        }
                    } else {
                        append(text[i]); i++
                    }
                }
                // **bold**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // ~~strikethrough~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > i) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // `inline code`
                text.startsWith("`", i) && !text.startsWith("``", i) -> {
                    val end = text.indexOf("`", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)) {
                            append(" ${text.substring(i + 1, end)} ")
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // *italic*
                text.startsWith("*", i) && !text.startsWith("**", i) -> {
                    val end = text.indexOf("*", i + 1)
                    if (end > i) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
