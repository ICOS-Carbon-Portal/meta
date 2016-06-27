#!/bin/bash

cd "$(dirname "$0")"

source config.sh

scp stop.sh start.sh restart.sh "$host:$deployPath"

rsync -aP ../target/scala-2.11/jobAd-assembly-1.0.jar "$host:$deployPath"assembly.jar

ssh "$host" "$deployPath"restart.sh
