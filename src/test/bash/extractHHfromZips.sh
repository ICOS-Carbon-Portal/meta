#!/bin/bash

HHFILE=`unzip -l "$1" | grep FULLSET_HH | awk '{print $4}'`

unzip -j "$1" "$HHFILE" -d $2
