package se.lu.nateko.cp.meta.rdfstore

enum Quota:
	case Unlimited
	case PerClient(clientId: String)

final case class SparqlDataset(defaultGraphs: Seq[String] = Nil, namedGraphs: Seq[String] = Nil):
	def isEmpty: Boolean = defaultGraphs.isEmpty && namedGraphs.isEmpty

final case class SparqlRequest(
	query: String,
	quota: Quota,
	dataset: SparqlDataset = SparqlDataset()
)
