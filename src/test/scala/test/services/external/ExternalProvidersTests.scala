package se.lu.nateko.cp.meta.test.services.external

import eu.icoscp.envri.Envri
import org.scalatest.funspec.AnyFunSpec
import se.lu.nateko.cp.meta.core.data.EnvriConfig
import se.lu.nateko.cp.meta.services.external.ExternalProviders
import se.lu.nateko.cp.meta.services.external.ExternalProviders.externalLandingPage

import java.net.URI

import ExternalTestObjects.*

class ExternalProvidersTests extends AnyFunSpec:

	private val providers = ExternalProviders(Map(
		Envri.SITES -> Seq(provider("meta.icos-cp.eu"))
	))

	private val icosLandingPage = URI("https://meta.icos-cp.eu/objects/abc")

	describe("lookup"):

		it("finds the provider by the landing page host"):
			given Envri = Envri.SITES
			assert(providers.lookup(icosLandingPage).map(_.host) === Some("meta.icos-cp.eu"))

		it("ignores the landing page path and scheme"):
			given Envri = Envri.SITES
			assert(providers.lookup(URI("http://meta.icos-cp.eu/some/other/path")).isDefined)

		it("does not match other hosts"):
			given Envri = Envri.SITES
			assert(providers.lookup(URI("https://meta.icos-cp.eu.example.org/objects/abc")) === None)
			assert(providers.lookup(URI("https://data.icos-cp.eu/objects/abc")) === None)

		it("only uses the providers of the current ENVRI"):
			given Envri = Envri.ICOS
			assert(providers.lookup(icosLandingPage) === None)

	describe("mintsLocalPid"):
		given Envri = Envri.SITES

		it("mints a local PID when there is no landing page"):
			assert(providers.mintsLocalPid(None))

		it("mints a local PID when the landing page belongs to no provider"):
			assert(providers.mintsLocalPid(Some(URI("https://unknown.example.org/obj"))))

		it("does not mint a local PID when the landing page belongs to a provider"):
			assert(!providers.mintsLocalPid(Some(icosLandingPage)))

	describe("externalLandingPage"):
		given EnvriConfig = sitesConf

		it("is empty for objects served locally"):
			assert(externalLandingPage(docObject(Some(localAccessUrl))) === None)

		it("is empty for objects without access URL"):
			assert(externalLandingPage(docObject(None)) === None)

		it("is the access URL when it differs from the local one"):
			val other = URI("https://unknown.example.org/obj")
			assert(externalLandingPage(docObject(Some(other))) === Some(other))
