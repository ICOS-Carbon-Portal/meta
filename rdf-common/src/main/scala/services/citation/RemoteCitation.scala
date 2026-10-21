package se.lu.nateko.cp.meta.services.citation

enum RemoteCitation:
	case NotApplicable
	case Expected(error: Option[String])
