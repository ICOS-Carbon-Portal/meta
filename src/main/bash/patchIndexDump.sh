#!/bin/bash
# patches URL for Trebon station to be based on proper id CZ-wet instead of CZ-Wet
cp sparqlMagicIndex.bin sparqlMagicIndex.bin.bak
cat sparqlMagicIndex.bin.bak | gunzip > sparqlMagicIndex.bin.unz
sed -i 's/ES_CZ-Wet/ES_CZ_wet/g' sparqlMagicIndex.bin.unz
cat sparqlMagicIndex.bin.unz | gzip > sparqlMagicIndex.bin