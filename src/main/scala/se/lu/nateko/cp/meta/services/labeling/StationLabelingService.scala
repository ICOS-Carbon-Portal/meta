package se.lu.nateko.cp.meta.services.labeling

import akka.event.LoggingAdapter
import org.eclipse.rdf4j.model.IRI
import org.eclipse.rdf4j.model.ValueFactory
import se.lu.nateko.cp.cpauth.core.UserId
import se.lu.nateko.cp.meta.LabelingServiceConfig
import se.lu.nateko.cp.meta.instanceserver.InstanceServer
import se.lu.nateko.cp.meta.instanceserver.LoggingInstanceServer
import se.lu.nateko.cp.meta.instanceserver.TriplestoreConnection
import se.lu.nateko.cp.meta.onto.InstOnto
import se.lu.nateko.cp.meta.onto.Onto
import se.lu.nateko.cp.meta.services.CpmetaVocab
import se.lu.nateko.cp.meta.services.FileStorageService
import se.lu.nateko.cp.meta.services.UnauthorizedStationUpdateException


class StationLabelingService(
	instanceServers: Map[String, InstanceServer],
	protected val onto: Onto,
	protected val fileStorage: FileStorageService,
	protected val metaVocab: CpmetaVocab,
	protected val config: LabelingServiceConfig,
	protected val log: LoggingAdapter
) extends UserInfoService with StationInfoService with FileService with LifecycleService:
	import LabelingDb.AnyLblConn
	import TriplestoreConnection.{getStringValues, getUriValues}

	protected val db = LabelingDb(
		provServer = instanceServers(config.provisionalInfoInstanceServerId),
		lblServer = instanceServers(config.instanceServerId),
		icosServer = instanceServers(config.icosMetaInstanceServerId)
	)
	protected given factory: ValueFactory = metaVocab.factory
	protected val vocab = new StationsVocab(factory)
	protected val protectedPredicates = Set(vocab.hasAssociatedFile, vocab.hasApplicationStatus)

	protected def assertThatWriteIsAuthorized(stationUri: IRI, uploader: UserId)(using AnyLblConn): Unit =
		if(!userIsPiOrDeputy(uploader, stationUri)) throw new UnauthorizedStationUpdateException(
			"Only PIs are authorized to update station's info"
		)

	protected def userIsPiOrDeputy(user: UserId, station: IRI)(using AnyLblConn): Boolean = {
		getStationPiOrDeputyEmails(station).contains(user.email.toLowerCase)
	}

	protected def getPiEmails(piUri: IRI)(using AnyLblConn): Seq[String] =
		getStringValues(piUri, vocab.hasEmail).map(_.toLowerCase)

	protected def lookupStationClass(stationUri: IRI)(using AnyLblConn): Option[IRI] =
		InstOnto.getSingleTypeIfAny(stationUri)

	protected def lookupStationId(stationUri: IRI)(using AnyLblConn): Option[String] =
		getStringValues(stationUri, vocab.hasShortName).headOption

	protected def getStationPiOrDeputyEmails(stationUri: IRI)(using AnyLblConn): Seq[String] = (
		getUriValues(stationUri, vocab.hasPi) ++
		getUriValues(stationUri, vocab.hasDeputyPi)
	).flatMap(getPiEmails)

end StationLabelingService
