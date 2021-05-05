update public.etccsv
set "OBJECT" = good.obj
from (
	select "SUBJECT" as subj, "PREDICATE" as pred, "OBJECT" as obj
	from public.etccsv
	where
		"ASSERTION" = 'true'::boolean and
		tstamp > '2021-05-05 16:00:00+02'::timestamptz and
		tstamp < '2021-05-05 16:10:00+02'::timestamptz and
		"PREDICATE" = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith'
	) as good
where tstamp < '2021-05-05 10:00:00+02'::timestamptz and "SUBJECT" = good.subj and "PREDICATE" = good.pred
;


select "SUBJECT" as subj, "OBJECT" as obj
from public.etccsv
where
	tstamp < '2021-05-05 16:00:00+02'::timestamptz and
	substring("OBJECT", 52, 1) = '-' and
	"PREDICATE" = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith';


delete from public.etccsv
where
	tstamp > '2021-05-05 16:00:00+02'::timestamptz and
	tstamp < '2021-05-05 16:10:00+02'::timestamptz and
	"PREDICATE" = 'http://meta.icos-cp.eu/ontologies/cpmeta/wasPerformedWith'
;