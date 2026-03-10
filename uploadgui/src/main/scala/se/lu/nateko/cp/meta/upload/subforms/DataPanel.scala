package se.lu.nateko.cp.meta.upload.subforms

import scala.language.unsafeNulls

import scala.util.Try

import se.lu.nateko.cp.meta.upload.*
import se.lu.nateko.cp.meta.{UploadDto, DataObjectDto}

import formcomponents.*
import Utils.*
import se.lu.nateko.cp.meta.SubmitterProfile
import UploadApp.whenDone
import java.net.URI
import java.time.Instant
import eu.icoscp.envri.Envri


class DataPanel(
	objSpecs: IndexedSeq[ObjSpec],
	gcmdKeywords: IndexedSeq[String],
	submitter: () => Option[SubmitterProfile]
)(using bus: PubSubBus, envri: Envri) extends PanelSubform(".data-section"){
	def nRows: Try[Option[Int]] = nRowsInput.value.withErrorContext("Number of rows")
	def objSpec: Try[ObjSpec] = objSpecSelect.value.withMissingError("Data type not set")
	def keywords: Try[Seq[String]] = extraKeywords.values
	def licence: Try[Option[URI]] = licenceUrl.value.withErrorContext("Data licence URL")
	def moratorium: Try[Option[Instant]] = moratoriumInput.value.withErrorContext("Delayed publication instant (moratorium)")

	private val levelControl = new Radio[Int]("level-radio", onLevelSelected, s => Try(s.toInt).toOption, _.toString)
	private val objSpecSelect = new Select[ObjSpec]("objspecselect", _.name, _.uri.toString, cb = onSpecSelected)
	private val nRowsInput = new IntOptInput("nrows", notifyUpdate)
	private val dataTypeKeywords = new TagCloud("data-keywords")
	private val keywordsInput = new TextInput("keywords", () => (), "keywords")
	private val keywordList = new KeywordDataList("keyword-list")
	keywordList.values = gcmdKeywords
	private val licenceUrl = new UriOptInput("licenceselect", notifyUpdate)
	private val moratoriumInput = new InstantOptInput("moratoriuminput", notifyUpdate)
	private val extraKeywordsDiv = new HtmlElements(".keywords-block")
	private val extraKeywords = new DataListForm("extra-keywords", keywordList, notifyUpdate)
	private val varInfoButton = new Button("data-type-variable-list-button", showVarInfoModal)
	private val varInfoModal = new Modal("data-type-info-modal")


	def resetForm(): Unit = {
		levelControl.value = Int.MinValue
		objSpecSelect.setOptions(IndexedSeq.empty)
		nRowsInput.value = None
		dataTypeKeywords.setList(Seq.empty)
		keywordsInput.value = ""
		licenceUrl.value = None
		moratoriumInput.value = None
		extraKeywords.setValues(Seq())
		disableVarInfoButton()
	}

	bus.subscribe{
		case GotUploadDto(dto) => handleDto(dto)
	}

	private def onLevelSelected(level: Int): Unit = {

		val levelFilter: ObjSpec => Boolean = _.dataLevel == level

		val specFilter = submitter().fold(levelFilter)(subm => {

			val themeOk = (spec: ObjSpec) => subm.authorizedThemes.fold(true)(_.contains(spec.theme))
			val projOk = (spec: ObjSpec) => subm.authorizedProjects.fold(true)(_.contains(spec.project))

			spec => themeOk(spec) && projOk(spec) && levelFilter(spec)
		})

		objSpecSelect.setOptions(objSpecs.filter(specFilter))
		dataTypeKeywords.setList(Seq.empty)
		disableVarInfoButton()
		bus.publish(LevelSelected(level))
	}

	private def onSpecSelected(): Unit = {
		objSpecSelect.value.foreach{ objSpec =>
			val objFormat = objSpec.format.toString.split("/").last
			val isNotAtcTimeSeries = objFormat != "asciiAtcProductTimeSer" // TODO: Find a better place for these constants
			val isNotWdcgg = objFormat != "asciiWdcggTimeSer"
			val isNotNetCDF = objFormat != "netcdfTimeSeries"

			if(envri == Envri.SITES && !objSpec.isSitesProjectData) extraKeywordsDiv.hide() else extraKeywordsDiv.show()
			if(objSpec.isStationTimeSer && isNotAtcTimeSeries && isNotWdcgg && isNotNetCDF) nRowsInput.enable() else nRowsInput.disable()
			if(objSpec.dataset.nonEmpty) varInfoButton.enable() else disableVarInfoButton()
			dataTypeKeywords.setList(objSpec.keywords)
			objSpec.dataset.foreach(dataset => {
				whenDone(getVariables(dataset)) { variables =>
					bus.publish(GotVariableList(variables))
				}
			})
			bus.publish(ObjSpecSelected(objSpec))
		}
		notifyUpdate()
	}

	private def disableVarInfoButton(): Unit = {
		varInfoButton.disable("No data type with variable info is selected")
	}

	private def showVarInfoModal(): Unit = for(
		spec <- objSpecSelect.value;
		dsSpec <- spec.dataset
	){
		val variablesInfo = if spec.isSpatiotemporal then
				Backend.getDatasetVariables(dsSpec)
			else
				Backend.getDatasetColumns(dsSpec)
		whenDone(variablesInfo){ datasetVars =>
			val tableHeader = """<table class="table"><thead><th>Label</th><th>Value type</th><th>Unit</th><th>Required</th><th>Regex</th><th>Title</th></thead><tbody>"""
			varInfoModal.setTitle(s"Variables in ${spec.name}")
			varInfoModal.setBody(
				datasetVars
					.map{ column => s"""<tr>
						|	<td>${column.label}</td>
						|	<td>${column.valueType}</td>
						|	<td>${column.unit}</td>
						|	<td>${if(column.isOptional) "" else """<i class="fas fa-check"></i>"""}</td>
						|	<td>${if(column.isRegex) """<i class="fas fa-check"></i>""" else ""}</td>
						|	<td>${column.title}</td>
						|</tr>""".stripMargin
					}
					.mkString(tableHeader, "", "</tbody></table>")
			)
		}
	}

	private def handleDto(upDto: UploadDto): Unit = upDto match {
		case dto: DataObjectDto =>
			objSpecs.find(_.uri == dto.objectSpecification).foreach{spec =>
				levelControl.value = spec.dataLevel
				onLevelSelected(spec.dataLevel)
				objSpecSelect.value = spec
				onSpecSelected()
			}
			extraKeywords.setValues(dto.references.flatMap(_.keywords).getOrElse(Seq()))
			licenceUrl.value = dto.references.flatMap(_.licence)
			moratoriumInput.value = dto.references.flatMap(_.moratorium)
			dto.specificInfo.fold(
				_ => nRowsInput.reset(),
				stationTs => nRowsInput.value = stationTs.nRows
			)
			show()

		case _ =>
			hide()
	}
}
