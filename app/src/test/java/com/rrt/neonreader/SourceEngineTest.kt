package com.rrt.neonreader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.jsoup.Jsoup

/** Offline regression tests; these do not assert compatibility with live websites. */
class SourceEngineTest {
    @Test fun normalizesUrls() {
        assertEquals("https://example.com",SourceEngine.normalizeBase("example.com/"))
        assertEquals("https://example.com/path",SourceEngine.normalizeBase("https://example.com/path/"))
    }
    @Test fun layoutsHaveSelectors() {
        for(layout in listOf("madara","themesia","generic")) {
            val s=SourceEngine.template("example.com","test",layout,adult=true)
            assertTrue(s.adult)
            assertTrue(s.itemSelector.isNotBlank())
            assertTrue(s.chapterSelector.isNotBlank())
            assertTrue(s.pageSelector.isNotBlank())
        }
    }
    @Test fun adultDomainsCanBeRegisteredWithoutNetworkApproval() {
        for (domain in listOf("nhentai.net", "hentai2read.com", "manhwaread.com")) {
            val source=SourceEngine.prepareSource("https://$domain", adult=true)
            assertEquals("https://$domain",source.baseUrl)
            assertTrue(source.adult)
            assertEquals("generic",source.template)
        }
    }
    @Test fun aSourceNameCanBeInferredFromUrl() {
        val source=SourceEngine.prepareSource("https://example.com", "")
        assertEquals("example.com",source.name)
    }
    @Test fun htmlSelectorsFindCards() {
        val sample="""<div class='listupd'><div class='bs'><div class='bsx'><a title='Test' href='https://example.com/series/test'><img src='https://example.com/image.jpg'></a><div class='tt'>Test</div></div></div></div>"""
        val doc=Jsoup.parse(sample)
        val selector=SourceEngine.template("example.com","Site","themesia").itemSelector
        assertTrue(doc.select(selector).isNotEmpty())
    }
}
