package se.lu.nateko.cp.meta.ingestion.badm

import java.time.format.DateTimeFormatter

object BadmConsts {

	val badmDateRegex = """(\d{4})(\d\d)(\d\d)""".r
	//TODO Support time in BADM as well, not only dates
	val badmDateAnyTimeRegex = """(\d{4})(\d\d)(\d\d)\d{4}""".r
	val yearRegex = "(\\d{4})".r
	val varCodeRegex = "(.+)_\\d+_\\d+_\\d+".r
	val standardNameRegex = """\s*(\w+)\s+(\w+)\s*""".r
	val withMiddleNameRegex = """(\w\.\s\w+)\s(\w+)\s*""".r

	val americanDateTime = DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a")

	val SiteVar = "SITE_ID"
	val SiteIdVar = "SITE_ID"
	val SiteNameVar = "SITE_NAME"

	val TeamMemberVar = "TEAM_MEMBER_NAME"
	val TeamMemberNameVar = "TEAM_MEMBER_NAME"
	val TeamMemberRoleVar = "TEAM_MEMBER_ROLE"
	val TeamMemberEmailVar = "TEAM_MEMBER_EMAIL"
	val TeamMemberInstVar = "TEAM_MEMBER_INSTITUTION"

	val LocationVar = "LOCATION_LAT"
	val LocationLatVar = "LOCATION_LAT"
	val LocationLonVar = "LOCATION_LONG"
	val LocationElevVar = "LOCATION_ELEV"

	val InstrumentVar = "INSTOM_MODEL"
	val VariableVar = "VARIABLE_H_V_R"
	val VarCodeVar = "VAR_CODE"

	val ValueQualifier = "VALUE"
	val DateQualifier = "DATE"
	val PercentQualifier = "PERC"
}