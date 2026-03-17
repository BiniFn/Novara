package eu.kanade.tachiyomi.source.online

import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * A simple implementation for sources from a website using Jsoup, an HTML parser.
 * Ported from Mihon source-api for extension compatibility.
 */
@Suppress("unused")
abstract class ParsedHttpSource : HttpSource() {

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     */
    override fun popularContentParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(popularContentSelector()).map { element ->
            popularContentFromElement(element)
        }

        val hasNextPage = popularContentNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun popularContentSelector(): String

    protected abstract fun popularContentFromElement(element: Element): SManga

    protected abstract fun popularContentNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     */
    override fun searchContentParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(searchContentSelector()).map { element ->
            searchContentFromElement(element)
        }

        val hasNextPage = searchContentNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun searchContentSelector(): String

    protected abstract fun searchContentFromElement(element: Element): SManga

    protected abstract fun searchContentNextPageSelector(): String?

    /**
     * Parses the response from the site and returns a [MangasPage] object.
     */
    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }

        val hasNextPage = latestUpdatesNextPageSelector()?.let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    protected abstract fun latestUpdatesSelector(): String

    protected abstract fun latestUpdatesFromElement(element: Element): SManga

    protected abstract fun latestUpdatesNextPageSelector(): String?

    /**
     * Parses the response from the site and returns the details of a manga.
     */
    override fun mangaDetailsParse(response: Response): SManga {
        return mangaDetailsParse(response.asJsoup())
    }

    protected abstract fun mangaDetailsParse(document: Document): SManga

    /**
     * Parses the response from the site and returns a list of chapters.
     */
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }
    }

    protected abstract fun chapterListSelector(): String

    protected abstract fun chapterFromElement(element: Element): SChapter

    /**
     * Parses the response from the site and returns the page list.
     */
    override fun pageListParse(response: Response): List<Page> {
        return pageListParse(response.asJsoup())
    }

    protected abstract fun pageListParse(document: Document): List<Page>

    /**
     * Parse the response from the site and returns the absolute url to the source image.
     */
    override fun imageUrlParse(response: Response): String {
        return imageUrlParse(response.asJsoup())
    }

    protected abstract fun imageUrlParse(document: Document): String
}
