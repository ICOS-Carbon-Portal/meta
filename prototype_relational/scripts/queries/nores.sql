
SELECT DISTINCT v11."id1m2" AS "id1m2", v34."prov" AS "prov", v11."v0" AS "v0", v17."v1" AS "v1", CASE WHEN v51."v11" IS NOT NULL THEN CASE     WHEN v51."v11" = 0 THEN ('https://meta.icos-cp.eu/collections/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id1m12", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    WHEN v51."v11" = 1 THEN ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id3m13", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    WHEN v51."v11" = 2 THEN ('http://meta.icos-cp.eu/resources/nextvcoll_' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id8m14", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    ELSE NULL 
END ELSE NULL END AS "v1m21"
FROM ((SELECT v3."ended_at_time" AS "ended_at_time2m119", v1."has_name" AS "has_name26m94", v1."has_size_in_bytes" AS "has_size_in_bytes1m115", v1."id" AS "id1m2", CAST(NULL AS BIGINT) AS "v0"
FROM "ct_static_objects" v1, "ct_data_acquisitions" v2, "ct_data_submissions" v3, (VALUES  ('ES_NL-Loo'), ('ES_DE-Geb'), ('ES_DK-Vng'), ('ES_FR-Gri'), ('ES_SE-Htm'), ('ES_SE-Sto'), ('ES_CH-Dav'), ('ES_IT-Cp2'), ('ES_DE-HoH'), ('ES_BE-Lon'), ('ES_GL-ZaF'), ('ES_FR-Hes'), ('ES_DK-Sor'), ('ES_DE-Tha'), ('ES_FI-Sod'), ('ES_IT-SR2'), ('ES_BE-Dor'), ('ES_IT-BCi'), ('ES_FR-Bil'), ('ES_FI-Sii'), ('ES_UK-AMo'), ('ES_FR-Fon'), ('ES_BE-Maa'), ('ES_SE-Nor'), ('ES_SE-Svb'), ('ES_BE-Vie'), ('ES_IT-Ren'), ('ES_BE-Bra'), ('ES_FI-Hyy'), ('ES_CZ-Lnz'), ('ES_FR-Pue'), ('ES_CZ-BK1'), ('ES_FR-FBn'), ('ES_NO-Hur'), ('ES_FR-Lus'), ('ES_DE-RuS'), ('ES_SE-Deg'), ('ES_FR-Lqu'), ('ES_FR-Lam'), ('ES_IT-MBo')) AS v4 ("was_associated_with1m3")
WHERE (v1."has_start_time" IS NOT NULL AND v1."has_size_in_bytes" IS NOT NULL AND v1."has_name" IS NOT NULL AND v3."ended_at_time" IS NOT NULL AND v1."was_acquired_by" = v2."id" AND v1."was_submitted_by" = v3."id" AND v2."was_associated_with" = v4."was_associated_with1m3" AND 'etcL2Meteosens' = v1."has_object_spec")
)UNION ALL 
(SELECT v8."ended_at_time" AS "ended_at_time2m119", v6."has_name" AS "has_name26m94", v6."has_size_in_bytes" AS "has_size_in_bytes1m115", v6."id" AS "id1m2", 0 AS "v0"
FROM "ct_static_objects" v6, "ct_data_acquisitions" v7, "ct_data_submissions" v8, (VALUES  ('ES_NL-Loo'), ('ES_DE-Geb'), ('ES_DK-Vng'), ('ES_FR-Gri'), ('ES_SE-Htm'), ('ES_SE-Sto'), ('ES_CH-Dav'), ('ES_IT-Cp2'), ('ES_DE-HoH'), ('ES_BE-Lon'), ('ES_GL-ZaF'), ('ES_FR-Hes'), ('ES_DK-Sor'), ('ES_DE-Tha'), ('ES_FI-Sod'), ('ES_IT-SR2'), ('ES_BE-Dor'), ('ES_IT-BCi'), ('ES_FR-Bil'), ('ES_FI-Sii'), ('ES_UK-AMo'), ('ES_FR-Fon'), ('ES_BE-Maa'), ('ES_SE-Nor'), ('ES_SE-Svb'), ('ES_BE-Vie'), ('ES_IT-Ren'), ('ES_BE-Bra'), ('ES_FI-Hyy'), ('ES_CZ-Lnz'), ('ES_FR-Pue'), ('ES_CZ-BK1'), ('ES_FR-FBn'), ('ES_NO-Hur'), ('ES_FR-Lus'), ('ES_DE-RuS'), ('ES_SE-Deg'), ('ES_FR-Lqu'), ('ES_FR-Lam'), ('ES_IT-MBo')) AS v9 ("was_associated_with1m7")
WHERE (v7."started_at_time" IS NOT NULL AND v6."has_size_in_bytes" IS NOT NULL AND v6."has_name" IS NOT NULL AND v8."ended_at_time" IS NOT NULL AND v6."was_acquired_by" = v7."id" AND v6."was_submitted_by" = v8."id" AND v7."was_associated_with" = v9."was_associated_with1m7" AND 'etcL2Meteosens' = v6."has_object_spec")
)) v11
 JOIN 
((SELECT v12."id" AS "id1m0", CAST(NULL AS BIGINT) AS "v1"
FROM "ct_static_objects" v12
WHERE v12."has_end_time" IS NOT NULL
)UNION ALL 
(SELECT v14."id" AS "id1m0", 0 AS "v1"
FROM "ct_static_objects" v14, "ct_data_acquisitions" v15
WHERE (v15."ended_at_time" IS NOT NULL AND v14."was_acquired_by" = v15."id")
)) v17 ON v11."id1m2" = v17."id1m0" 
 LEFT OUTER JOIN 
((SELECT CAST(NULL AS TEXT) AS "is_next_version_of1m10", v23."next_version3m8" AS "next_version3m8", CAST(NULL AS TEXT) AS "next_version3m9", 'ontop-provenance-constant' AS "prov", 0 AS "v7"
FROM (SELECT DISTINCT v20."id1m46" AS "id1m46", "next_version3m8" AS "next_version3m8"
FROM (SELECT v18."id" AS "id1m46", v18."is_next_version_of" AS "is_next_version_of"
FROM "ct_collections" v18
) v20 JOIN LATERAL unnest(v20."is_next_version_of")  WITH ORDINALITY AS v21("next_version3m8", "index") ON TRUE
WHERE "next_version3m8" IS NOT NULL
) v23
)UNION ALL 
(SELECT CAST(NULL AS TEXT) AS "is_next_version_of1m10", CAST(NULL AS TEXT) AS "next_version3m8", v30."next_version3m9" AS "next_version3m9", 'ontop-provenance-constant' AS "prov", 1 AS "v7"
FROM (SELECT DISTINCT v27."id3m46" AS "id3m46", "next_version3m9" AS "next_version3m9"
FROM (SELECT v25."id" AS "id3m46", v25."is_next_version_of" AS "is_next_version_of2"
FROM "ct_static_objects" v25
) v27 JOIN LATERAL unnest(v27."is_next_version_of2")  WITH ORDINALITY AS v28("next_version3m9", "index") ON TRUE
WHERE "next_version3m9" IS NOT NULL
) v30
)UNION ALL 
(SELECT v32."is_next_version_of" AS "is_next_version_of1m10", CAST(NULL AS TEXT) AS "next_version3m8", CAST(NULL AS TEXT) AS "next_version3m9", 'ontop-provenance-constant' AS "prov", 2 AS "v7"
FROM "ct_plain_collections" v32
WHERE v32."is_next_version_of" IS NOT NULL
)) v34 ON ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v11."id1m2", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D')) = CASE     WHEN v34."v7" = 0 THEN v34."next_version3m8"
    WHEN v34."v7" = 1 THEN v34."next_version3m9"
    WHEN v34."v7" = 2 THEN ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v34."is_next_version_of1m10", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    ELSE NULL
END
 LEFT OUTER JOIN
((SELECT v40."id1m12" AS "id1m12", CAST(NULL AS TEXT) AS "id3m13", CAST(NULL AS TEXT) AS "id8m14", CAST(NULL AS TEXT) AS "is_next_version_of1m17", v40."next_version3m15" AS "next_version3m15", CAST(NULL AS TEXT) AS "next_version3m16", 0 AS "v11"
FROM (SELECT DISTINCT v37."id1m12" AS "id1m12", "next_version3m15" AS "next_version3m15"
FROM (SELECT v35."id" AS "id1m12", v35."is_next_version_of" AS "is_next_version_of4"
FROM "ct_collections" v35
) v37 JOIN LATERAL unnest(v37."is_next_version_of4")  WITH ORDINALITY AS v38("next_version3m15", "index") ON TRUE
WHERE "next_version3m15" IS NOT NULL
) v40
)UNION ALL
(SELECT CAST(NULL AS TEXT) AS "id1m12", v47."id3m13" AS "id3m13", CAST(NULL AS TEXT) AS "id8m14", CAST(NULL AS TEXT) AS "is_next_version_of1m17", CAST(NULL AS TEXT) AS "next_version3m15", v47."next_version3m16" AS "next_version3m16", 1 AS "v11"
FROM (SELECT DISTINCT v44."id3m13" AS "id3m13", "next_version3m16" AS "next_version3m16"
FROM (SELECT v42."id" AS "id3m13", v42."is_next_version_of" AS "is_next_version_of6"
FROM "ct_static_objects" v42
) v44 JOIN LATERAL unnest(v44."is_next_version_of6")  WITH ORDINALITY AS v45("next_version3m16", "index") ON TRUE
WHERE "next_version3m16" IS NOT NULL
) v47
)UNION ALL
(SELECT CAST(NULL AS TEXT) AS "id1m12", CAST(NULL AS TEXT) AS "id3m13", v49."id" AS "id8m14", v49."is_next_version_of" AS "is_next_version_of1m17", CAST(NULL AS TEXT) AS "next_version3m15", CAST(NULL AS TEXT) AS "next_version3m16", 2 AS "v11"
FROM "ct_plain_collections" v49
WHERE v49."is_next_version_of" IS NOT NULL
)) v51 ON ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v11."id1m2", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D')) = CASE     WHEN v51."v11" = 0 THEN v51."next_version3m15"
    WHEN v51."v11" = 1 THEN v51."next_version3m16"
    WHEN v51."v11" = 2 THEN ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."is_next_version_of1m17", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    ELSE NULL
END
WHERE (v51."v11" IS NULL OR CASE     WHEN v51."v11" = 0 THEN ('https://meta.icos-cp.eu/collections/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id1m12", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    WHEN v51."v11" = 1 THEN ('https://meta.icos-cp.eu/objects/' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id3m13", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    WHEN v51."v11" = 2 THEN ('http://meta.icos-cp.eu/resources/nextvcoll_' || REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(v51."id8m14", '%', '%25'), ' ', '%20'), '!', '%21'), '"', '%22'), '#', '%23'), '$', '%24'), '&', '%26'), '''', '%27'), '(', '%28'), ')', '%29'), '*', '%2A'), '+', '%2B'), ',', '%2C'), '/', '%2F'), ':', '%3A'), ';', '%3B'), '<', '%3C'), '=', '%3D'), '>', '%3E'), '?', '%3F'), '@', '%40'), '[', '%5B'), '\', '%5C'), ']', '%5D'), '^', '%5E'), '`', '%60'), '{', '%7B'), '|', '%7C'), '}', '%7D'))
    ELSE NULL
END IS NULL)
