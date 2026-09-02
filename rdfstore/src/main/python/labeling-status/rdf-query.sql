SELECT
	tstamp,
	"SUBJECT",
	CASE
		WHEN "OBJECT" = $$SUBMITTED$$ THEN $$STEP1SUBMITTED$$
		WHEN "OBJECT" = $$ACKNOWLEDGED$$ THEN $$STEP1ACKNOWLEDGED$$
		WHEN "OBJECT" = $$APPROVED$$ THEN $$STEP1APPROVED$$ 
		ELSE "OBJECT"
	END
FROM labeling
WHERE "ASSERTION" = true AND "PREDICATE" = $$http://meta.icos-cp.eu/ontologies/stationentry/hasApplicationStatus$$ ORDER BY "SUBJECT", tstamp
