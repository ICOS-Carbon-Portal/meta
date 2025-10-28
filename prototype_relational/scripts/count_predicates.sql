SELECT pred, COUNT(*) as count
FROM rdf_triples
GROUP BY pred
ORDER BY count DESC;
