package com.blindtechnexus.freeeloquencetts

import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.AlignmentSpan
import android.text.style.BackgroundColorSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan

/**
 * The styles carried in styled text, read out in words.
 *
 * A caller does not always hand over plain text: an app reading its own
 * screen may pass bold, italic, coloured or linked stretches along with the
 * words, and the service used to drop all of it on the floor with a single
 * `toString()`. With [Settings.readTextStyles] on, every styled stretch is
 * announced instead: "bold hello end bold", "link App store end link", "red
 * warning end red".
 *
 * What is announced, and the whole of it:
 * - bold, italic and bold italic (StyleSpan)
 * - underline, strikethrough, superscript, subscript
 * - links (URLSpan, and any other ClickableSpan)
 * - images, with whatever the page said they show (ImageSpan)
 * - monospace and other named fonts (TypefaceSpan)
 * - larger and smaller text, and other resized text (RelativeSizeSpan,
 *   AbsoluteSizeSpan)
 * - the colour of coloured text, by name (ForegroundColorSpan)
 * - highlighted text, with the colour of the highlight
 *   (BackgroundColorSpan)
 * - bullets and quotes (BulletSpan, QuoteSpan)
 * - centred and right-aligned text (AlignmentSpan)
 *
 * The web is the reason most of the second half of that list exists. A page
 * read by a browser arrives here already flattened to text, and the flattening
 * keeps the meaning in spans: a link is a URLSpan, a heading is a StyleSpan
 * with a RelativeSizeSpan beside it, a list item is a BulletSpan, a picture is
 * an ImageSpan. Read without them, all of that is a wall of words in which a
 * link is indistinguishable from the sentence around it and a picture is
 * nothing at all -- which is what "the page read flat" sounds like. So every
 * one of them is announced as the thing it is, twice, around the words it
 * covers: "link Shop end link".
 *
 * A clickable span that is not a URL is still a link: on Android the
 * difference between the two is which method the app overrides, not what a
 * listener would hear, and a page whose links are handled in its own code
 * rather than by the browser carries the plain kind. An image has no words of
 * its own to wrap, so it is announced where it stands and the placeholder
 * character under it is not read -- "image picture of a red bicycle" -- which
 * is the one case where a span replaces text rather than describing it.
 *
 * What is deliberately not announced: black and white foreground colours,
 * which are the default ink on one screen or another and would be noise on
 * every stretch of every app that sets its colours explicitly; transparent
 * colours, which colour nothing; and layout spans that say nothing about a
 * word (margins, line heights, spell-check marks).
 *
 * Total: unknown spans are skipped, misbehaving spans are caught, and
 * anything that goes wrong anywhere in here answers the plain text, because
 * a style that cannot be read is worth the words without it and never worth
 * losing the utterance over.
 */
object TextStyles {

    /** The most spans one text is ever asked about. Past this the rest are
     *  skipped, because a hostile text is not owed an unbounded walk. */
    private const val MAX_SPANS = 500

    /** [text] with its styles announced, or the plain text when it carries
     *  none -- or when anything about it refuses to answer. */
    fun annotate(text: CharSequence?): String {
        if (text == null) return ""
        return try {
            if (text !is Spanned) return text.toString()
            if (text.isEmpty()) return ""
            val spans = try {
                text.getSpans(0, text.length, Any::class.java)
            } catch (broken: Exception) {
                return text.toString()
            }
            if (spans.isEmpty()) return text.toString()
            val marks = ArrayList<Mark>(spans.size * 2)
            // Only allocated when something in the text is a thing rather
            // than a description of the words: an image stands where a
            // character stands, and that character is not a word.
            var replaced: BooleanArray? = null
            var count = 0
            for (span in spans) {
                if (count >= MAX_SPANS) break
                val label = try {
                    labelOf(span)
                } catch (broken: Exception) {
                    continue
                } ?: continue
                // A try that answers with the bounds rather than assigning
                // them: a span whose ends cannot be read is skipped whole,
                // and nothing is left half-initialised for the rest of the
                // loop to trip over.
                val bounds = try {
                    text.getSpanStart(span) to text.getSpanEnd(span)
                } catch (broken: Exception) {
                    continue
                }
                val from = bounds.first
                val to = bounds.second
                if (from < 0 || to <= from || from >= text.length) continue
                val start = from.coerceIn(0, text.length)
                val end = to.coerceIn(0, text.length)
                if (end <= start) continue
                if (span is ImageSpan) {
                    // An image is not words with a style on them; it is a
                    // thing standing where a character stands. The character
                    // it stands on -- U+FFFC, an object replacement -- is not
                    // read, and what is read instead is the word image and
                    // whatever description the page gave it.
                    val described = imageOf(span)
                    if (replaced == null) replaced = BooleanArray(text.length)
                    for (at in start until end) replaced[at] = true
                    marks.add(Mark(start, false, true, described))
                    count++
                    continue
                }
                if (label.isEmpty()) {
                    // A marker that stands on its own, with no end to it.
                    val single = try {
                        singleOf(span)
                    } catch (broken: Exception) {
                        continue
                    } ?: continue
                    marks.add(Mark(start, false, true, single))
                } else {
                    marks.add(Mark(start, false, false, label))
                    marks.add(Mark(end, true, false, label))
                }
                count++
            }
            if (marks.isEmpty()) return text.toString()
            // Ends before starts at the same offset, so "end bold italic"
            // reads in the order the styles do.
            marks.sortWith(compareBy<Mark> { it.at }.thenBy { if (it.end) 0 else 1 })
            val out = StringBuilder(text.length + marks.size * 8)
            var i = 0
            var m = 0
            while (i <= text.length) {
                while (m < marks.size && marks[m].at == i) {
                    val mark = marks[m]
                    if (mark.end) out.append(" end ").append(mark.label).append(' ')
                    else out.append(' ').append(mark.label).append(' ')
                    m++
                }
                // A mark stranded behind (it cannot be, the list is sorted,
                // but a span that moved under us is not owed an infinite
                // loop) is dropped rather than chased.
                while (m < marks.size && marks[m].at < i) m++
                if (i < text.length) {
                    // The character under an image is a placeholder, and a
                    // placeholder read out loud is a wrong word between the
                    // image and what it shows.
                    val skip = replaced
                    if (skip == null || !skip[i]) out.append(text[i])
                }
                i++
            }
            collapse(out.toString())
        } catch (broken: Throwable) {
            try {
                text.toString()
            } catch (failed: Throwable) {
                ""
            }
        }
    }

    private class Mark(val at: Int, val end: Boolean, val single: Boolean, val label: String)

    /**
     * What [span] announces, or nothing when it is not a style with a name.
     * An empty string means a span whose marker stands alone (see
     * [singleOf]); null means no marker at all.
     */
    private fun labelOf(span: Any): String? = when (span) {
        is StyleSpan -> when (span.style and Typeface.BOLD_ITALIC) {
            Typeface.BOLD -> "bold"
            Typeface.ITALIC -> "italic"
            Typeface.BOLD_ITALIC -> "bold italic"
            else -> null
        }
        is UnderlineSpan -> "underline"
        is StrikethroughSpan -> "strikethrough"
        is SuperscriptSpan -> "superscript"
        is SubscriptSpan -> "subscript"
        is URLSpan -> "link"
        // Any other span that a finger can press is a link too, and a page
        // that handles its own links carries this kind rather than URLSpan.
        // The two are the same thing to a listener.
        is ClickableSpan -> "link"
        is BulletSpan -> ""
        is QuoteSpan -> "quote"
        is TypefaceSpan -> fontOf(span)
        is RelativeSizeSpan -> {
            val proportion = span.sizeChange
            when {
                proportion >= 1.05f -> "larger text"
                proportion <= 0.95f -> "smaller text"
                else -> null
            }
        }
        is AbsoluteSizeSpan -> "different size text"
        is ForegroundColorSpan -> foregroundOf(span.foregroundColor)
        is BackgroundColorSpan -> backgroundOf(span.backgroundColor)
        is AlignmentSpan -> when (span.alignment) {
            Layout.Alignment.ALIGN_CENTER -> "centered"
            Layout.Alignment.ALIGN_OPPOSITE -> "right aligned"
            else -> null
        }
        else -> null
    }

    private fun singleOf(span: Any): String? = when (span) {
        is BulletSpan -> "bullet"
        else -> null
    }

    /** What an image is: the word for it, and whatever the page called it. */
    private fun imageOf(span: ImageSpan): String {
        val described = try {
            // A description is Android 8's; before that an image has only the
            // name of the thing it was loaded from, which is what [source] is.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) span.contentDescription else null
        } catch (_: Throwable) {
            null
        }
        val said = try {
            span.source
        } catch (_: Throwable) {
            null
        }
        val label = (described?.takeIf { it.isNotBlank() } ?: said?.takeIf { it.isNotBlank() })
        if (label == null) return "image"
        val clean = label.filter { !it.isISOControl() }.trim().take(120)
        return if (clean.isEmpty()) "image" else "image $clean"
    }

    private fun fontOf(span: TypefaceSpan): String? {
        val family = try {
            span.family
        } catch (broken: Exception) {
            return null
        } ?: return "different font"
        if (family.isBlank()) return "different font"
        if (family.equals("monospace", ignoreCase = true)) return "monospace"
        val clean = family.filter { it.isLetterOrDigit() || it == ' ' }.trim().take(20)
        return if (clean.isEmpty()) "different font" else "font $clean"
    }

    private fun foregroundOf(color: Int): String? {
        if (alphaOf(color) < 24) return null
        val name = colorName(color) ?: return null
        // The default ink, on one screen or another, and noise everywhere
        // an app sets its colours explicitly.
        if (name == "black" || name == "white") return null
        return name
    }

    private fun backgroundOf(color: Int): String? {
        if (alphaOf(color) < 24) return null
        val name = colorName(color) ?: return null
        if (name == "white") return null
        return "highlighted $name"
    }

    private fun alphaOf(color: Int): Int = (color ushr 24) and 0xFF

    private class NamedColor(val name: String, val red: Int, val green: Int, val blue: Int)

    private val COLORS = listOf(
        NamedColor("black", 0, 0, 0),
        NamedColor("white", 255, 255, 255),
        NamedColor("red", 255, 0, 0),
        NamedColor("green", 0, 128, 0),
        NamedColor("blue", 0, 0, 255),
        NamedColor("yellow", 255, 255, 0),
        NamedColor("cyan", 0, 255, 255),
        NamedColor("magenta", 255, 0, 255),
        NamedColor("gray", 128, 128, 128),
        NamedColor("orange", 255, 165, 0),
        NamedColor("purple", 128, 0, 128),
        NamedColor("brown", 165, 42, 42),
        NamedColor("pink", 255, 192, 203),
        NamedColor("lime", 0, 255, 0),
        NamedColor("navy", 0, 0, 128),
        NamedColor("teal", 0, 128, 128),
        NamedColor("maroon", 128, 0, 0),
        NamedColor("olive", 128, 128, 0)
    )

    /** The nearest named colour to [color], by squared distance in RGB. */
    private fun colorName(color: Int): String? {
        val red = (color shr 16) and 0xFF
        val green = (color shr 8) and 0xFF
        val blue = color and 0xFF
        var best: NamedColor? = null
        var bestBy = Int.MAX_VALUE
        for (named in COLORS) {
            val dr = red - named.red
            val dg = green - named.green
            val db = blue - named.blue
            val by = dr * dr + dg * dg + db * db
            if (by < bestBy) {
                bestBy = by
                best = named
            }
        }
        return best?.name
    }

    /** Runs of whitespace down to one space, and the ends trimmed. */
    private fun collapse(text: String): String {
        val out = StringBuilder(text.length)
        var spaced = true
        for (c in text) {
            if (c.isWhitespace()) {
                if (!spaced) {
                    out.append(' ')
                    spaced = true
                }
            } else {
                out.append(c)
                spaced = false
            }
        }
        var end = out.length
        while (end > 0 && out[end - 1] == ' ') end--
        return if (end == out.length) out.toString() else out.substring(0, end)
    }
}
