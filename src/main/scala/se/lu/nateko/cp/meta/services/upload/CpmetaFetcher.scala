package se.lu.nateko.cp.meta.services.upload

import org.openrdf.model.URI
import org.openrdf.model.vocabulary.RDFS

import se.lu.nateko.cp.meta.core.data._

import se.lu.nateko.cp.meta.instanceserver.FetchingHelper
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.utils.sesame._

trait CpmetaFetcher extends FetchingHelper{
	protected def metaVocab: CpmetaVocab

	protected def getSpecification(spec: URI) = DataObjectSpec(
		format = getLabeledResource(spec, metaVocab.hasFormat),
		encoding = getLabeledResource(spec, metaVocab.hasEncoding),
		dataLevel = getSingleInt(spec, metaVocab.hasDataLevel),
		datasetSpec = None
	)

	protected def getSpatialCoverage(cov: URI) = SpatialCoverage(
		min = Position(
			lat = getSingleDouble(cov, metaVocab.hasSouthernBound),
			lon = getSingleDouble(cov, metaVocab.hasWesternBound)
		),
		max = Position(
			lat = getSingleDouble(cov, metaVocab.hasNothernBound),
			lon = getSingleDouble(cov, metaVocab.hasEasternBound)
		),
		label = getOptionalString(cov, RDFS.LABEL)
	)

	protected def getDataProduction(prod: URI) = DataProduction(
		creator = getLabeledResource(prod, metaVocab.wasPerformedBy),
		contributors = server.getUriValues(prod, metaVocab.wasParticipatedInBy).map(getLabeledResource),
		hostOrganization = getOptionalUri(prod, metaVocab.wasHostedBy).map(getLabeledResource),
		dateTime = getSingleInstant(prod, metaVocab.hasEndTime)
	)

	protected def getSubmission(subm: URI): DataSubmission = {
		val submitter: URI = getSingleUri(subm, metaVocab.prov.wasAssociatedWith)
		DataSubmission(
			submitter = UriResource(
				uri = submitter,
				label = getOptionalString(submitter, metaVocab.hasName)
			),
			start = getSingleInstant(subm, metaVocab.prov.startedAtTime),
			stop = getOptionalInstant(subm, metaVocab.prov.endedAtTime)
		)
	}

}