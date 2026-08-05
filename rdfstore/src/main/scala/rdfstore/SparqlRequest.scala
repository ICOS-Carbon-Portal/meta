package se.lu.nateko.cp.meta.rdfstore

enum Quota:
	case Unlimited
	case PerClient(clientId: String)

final case class SparqlRequest(query: String, quota: Quota)
