package se.lu.nateko.cp.meta.services

import org.eclipse.rdf4j.model.IRI
import se.lu.nateko.cp.meta.utils.rdf4j._

import se.lu.nateko.cp.meta.api.SparqlRunner
import se.lu.nateko.cp.meta.api.SparqlQuery
import se.lu.nateko.cp.meta.core.data.Envri._
import se.lu.nateko.cp.meta.core.data.Envri.EnvriConfigs
import java.net.URI

class ExportService(sparqler: SparqlRunner)(implicit configs: EnvriConfigs, envri: Envri) {
	val metaItemPrefix = configs(envri).metaItemPrefix
	val specsQuery = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|select ?spec
		|where{
		|	?spec cpmeta:hasDataLevel ?level .
		| VALUES ?level { 2 3 }
		|	FILTER NOT EXISTS {?spec cpmeta:hasAssociatedProject/cpmeta:hasHideFromSearchPolicy "true"^^xsd:boolean}
		|	FILTER(STRSTARTS(str(?spec), "$metaItemPrefix"))
		|}""".stripMargin
	val specs: Seq[URI] = sparqler.evaluateTupleQuery(SparqlQuery(specsQuery)).map(b =>
		b.getValue("spec").asInstanceOf[IRI].toJava
	).toSeq

	val query = s"""prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/>
		|prefix prov: <http://www.w3.org/ns/prov#>
		|select ?dobj ?spec ?fileName ?size ?submTime ?timeStart ?timeEnd
		|where {
		|	VALUES ?spec {${specs.mkString("<", "><", ">")}}
		|	?dobj cpmeta:hasObjectSpec ?spec .
		|	?dobj cpmeta:hasSizeInBytes ?size .
		|?dobj cpmeta:hasName ?fileName .
		|?dobj cpmeta:wasSubmittedBy/prov:endedAtTime ?submTime .
		|?dobj cpmeta:hasStartTime | (cpmeta:wasAcquiredBy / prov:startedAtTime) ?timeStart .
		|?dobj cpmeta:hasEndTime | (cpmeta:wasAcquiredBy / prov:endedAtTime) ?timeEnd .
		|	FILTER NOT EXISTS {[] cpmeta:isNextVersionOf ?dobj}
		|}
		|order by desc(?submTime)""".stripMargin

	def getRecords(): Seq[URI] = {
		sparqler.evaluateTupleQuery(SparqlQuery(query)).map(b =>
				b.getValue("dobj").asInstanceOf[IRI].toJava
		).toSeq
	}
}
