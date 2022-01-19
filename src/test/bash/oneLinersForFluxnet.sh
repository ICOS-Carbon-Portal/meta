cd winter2020

for f in *.zip; do ./extractHHfromZips.sh $f ../winter2020Hh; done

cd ../winter2020Hh

wc -l *.csv | awk '{print $0".zip"}' > lines.txt

for f in *.csv ; do zip $f".zip" $f; done

rm *.csv

sha256sum *.zip > hashes.txt

for f in *.zip; do unzip -l $f | tail -n 3 | grep FLUXNET | awk '{print $4".zip", $2}'; done > dates.txt

curl  -H "Accept: text/csv" -G --data-urlencode \
"query=prefix cpmeta: <http://meta.icos-cp.eu/ontologies/cpmeta/> select ?id where{?s a cpmeta:ES ; cpmeta:hasStationId ?id}" \
https://meta.icos-cp.eu/sparql | dos2unix | tail -n +2 | sort |
awk 'BEGIN {OFS=","; print "StationID", "IsICOS"} {print $1, "-1"}' > icosStatus.txt


join -j 2 lines.txt hashes.txt | 
join -j 1 - dates.txt |
awk 'BEGIN {OFS=","; print "StationID", "fileName", "Npoints", "hash", "creationDate"} {print substr($0,5,6), $1, ($2 - 1), $3, $4}' |
join -a 1 --header -t, -j 1 - icosStatus.txt > \
autoInfo.csv

#drought_data_hh.csv had CZ-Wet manuallyrenamed to CZ-wet,
#column "hash" renamed to "prevHash", and commas replaced with |
join -a 1 --header -t, -e "" -1 1 -2 8 \
-o 1.4,2.1,1.2,1.5,1.3,1.6,2.7,1.1,2.9,2.10,2.11,2.12,2.13,2.14,2.15,2.16,2.17,2.18,2.19,2.20,2.21,2.22,2.23,2.24,2.25 \
autoInfo.csv drought_data_hh.csv > winter_data_hh.csv

#change | back to ,

#reshape autoInfo.csv to facilitate copy-pasting updates to winter_data_hh.csv
awk 'BEGIN{FS=","; OFS=","} {print $4, "", $2, $5, $3, $6, "", $1}' > pre_data_hh.csv