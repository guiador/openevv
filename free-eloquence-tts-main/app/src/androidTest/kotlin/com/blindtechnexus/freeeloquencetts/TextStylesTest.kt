package com.blindtechnexus.freeeloquencetts

import android.graphics.Color
import android.graphics.Typeface
import android.text.Layout
import android.graphics.drawable.ColorDrawable
import android.text.SpannableString
import android.text.style.AlignmentSpan
import android.text.style.BulletSpan
import android.text.style.ClickableSpan
import android.text.style.ImageSpan
import android.text.style.ForegroundColorSpan
import android.text.style.QuoteSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.SuperscriptSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.text.style.UnderlineSpan
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The styles carried in styled text, read out in words.
 *
 * What is checked is that every kind of style is announced where it starts
 * and unannounced where it ends, that plain text comes through untouched,
 * and that the styles with nothing to say -- the default ink, the
 * transparent -- say nothing.
 */
@RunWith(AndroidJUnit4::class)
class TextStylesTest {

	@Test
	fun plainTextComesThroughUntouched() {
		assertEquals("hello", TextStyles.annotate("hello"))
		assertEquals("", TextStyles.annotate(""))
		assertEquals("", TextStyles.annotate(null))
		assertEquals("hello", TextStyles.annotate(SpannableString("hello")))
	}

	@Test
	fun boldIsAnnounced() {
		assertEquals("bold hello end bold", TextStyles.annotate(styled("hello", StyleSpan(Typeface.BOLD))))
	}

	@Test
	fun italicIsAnnouncedWhereItStands() {
		val text = SpannableString("say hello now")
		text.setSpan(StyleSpan(Typeface.ITALIC), 4, 9, 0)
		assertEquals("say italic hello end italic now", TextStyles.annotate(text))
	}

	@Test
	fun underlineAndSuperscriptAreAnnounced() {
		assertEquals(
			"underline hello end underline",
			TextStyles.annotate(styled("hello", UnderlineSpan()))
		)
		assertEquals(
			"superscript 2 end superscript",
			TextStyles.annotate(styled("2", SuperscriptSpan()))
		)
	}

	@Test
	fun linksAreAnnounced() {
		assertEquals(
			"link App store end link",
			TextStyles.annotate(styled("App store", URLSpan("https://example.com")))
		)
	}

	@Test
	fun monospaceIsAnnounced() {
		assertEquals(
			"monospace code end monospace",
			TextStyles.annotate(styled("code", TypefaceSpan("monospace")))
		)
	}

	@Test
	fun resizedTextIsAnnounced() {
		assertEquals(
			"larger text big end larger text",
			TextStyles.annotate(styled("big", RelativeSizeSpan(1.5f)))
		)
	}

	@Test
	fun colouredTextIsAnnouncedByColour() {
		assertEquals(
			"red warning end red",
			TextStyles.annotate(styled("warning", ForegroundColorSpan(Color.RED)))
		)
	}

	@Test
	fun theDefaultInkSaysNothing() {
		assertEquals("hello", TextStyles.annotate(styled("hello", ForegroundColorSpan(Color.BLACK))))
		assertEquals("hello", TextStyles.annotate(styled("hello", ForegroundColorSpan(Color.WHITE))))
		assertEquals("hello", TextStyles.annotate(styled("hello", ForegroundColorSpan(Color.TRANSPARENT))))
	}

	@Test
	fun bulletsStandAlone() {
		val text = SpannableString("item")
		text.setSpan(BulletSpan(), 0, 4, 0)
		assertEquals("bullet item", TextStyles.annotate(text))
	}

	@Test
	fun quotesAndCentringAreAnnounced() {
		assertEquals(
			"quote said end quote",
			TextStyles.annotate(styled("said", QuoteSpan()))
		)
		assertEquals(
			"centered title end centered",
			TextStyles.annotate(styled("title", AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)))
		)
	}

	/** A page whose links are its own is still a page with links in it: the
	 *  span Android uses for that is a ClickableSpan rather than a URLSpan,
	 *  and the two sound the same to somebody listening. */
	@Test
	fun anyClickableSpanIsALink() {
		val text = SpannableString("Press here now")
		text.setSpan(object : ClickableSpan() {
			override fun onClick(widget: android.view.View) = Unit
		}, 6, 10, 0)
		assertEquals("Press link here end link now", TextStyles.annotate(text))
	}

	/** A picture is not a word with a style on it, and the placeholder
	 *  character it stands on is not a word at all. What is heard is the word
	 *  image and what the page said the picture shows. */
	@Test
	fun anImageIsAnnouncedAndItsPlaceholderIsNot() {
		val text = SpannableString("see \uFFFC here")
		text.setSpan(ImageSpan(ColorDrawable(Color.RED), "a red square"), 4, 5, 0)
		val said = TextStyles.annotate(text)
		assertEquals("see image a red square here", said)
		assertFalse("the placeholder was read out: $said", said.contains('\uFFFC'))
	}

	/** An image with nothing said about it is still an image, and still not a
	 *  placeholder character in the middle of a sentence. */
	@Test
	fun anUndescribedImageIsStillAnImage() {
		val text = SpannableString("see \uFFFC")
		text.setSpan(ImageSpan(ColorDrawable(Color.RED)), 4, 5, 0)
		assertEquals("see image", TextStyles.annotate(text))
	}

	@Test
	fun overlappingStylesAreAllHeard() {
		val text = SpannableString("hello")
		text.setSpan(StyleSpan(Typeface.BOLD), 0, 5, 0)
		text.setSpan(StyleSpan(Typeface.ITALIC), 0, 5, 0)
		val said = TextStyles.annotate(text)
		assertTrue("bold missing from: $said", said.contains("bold"))
		assertTrue("italic missing from: $said", said.contains("italic"))
		assertTrue("the word missing from: $said", said.contains("hello"))
	}

	private fun styled(word: String, span: Any): SpannableString {
		val text = SpannableString(word)
		text.setSpan(span, 0, word.length, 0)
		return text
	}
}
