awk -F "," -vFPAT='[^,]*|"[^"]*"' -v OFS='\t' '{if ($5 == 0) {print "ATMO", $7, $6, $9, $10, $11, $12}}' atmos_data.csv | sort | uniq
