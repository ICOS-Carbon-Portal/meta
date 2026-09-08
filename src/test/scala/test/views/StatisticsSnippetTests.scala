package se.lu.nateko.cp.meta.test.views

import org.scalatest.funspec.AnyFunSpec

class StatisticsSnippetTests extends AnyFunSpec:

	private val statsUrl = "/objects/2mFUyfDgTsPjSA9TIhFqIn4-nAFqAB4v3PfhRIJPFYSb/statistics"

	describe("landing page statistics snippet"):

		it("renders placeholders instead of counts, and points to the statistics endpoint"):
			val html = views.html.landpagesnips.statistics(statsUrl, withPreviews = true).body
			assert(html.contains("""<span id="download-stats">-</span>"""))
			assert(html.contains("""<span id="preview-stats">-</span>"""))
			assert(html.contains(s"""fetch("$statsUrl""""))

		it("leaves out the previews placeholder for non-previewable objects"):
			val html = views.html.landpagesnips.statistics(statsUrl).body
			assert(html.contains("""<span id="download-stats">-</span>"""))
			assert(!html.contains("""<span id="preview-stats">"""))
