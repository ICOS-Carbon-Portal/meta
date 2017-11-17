#!/bin/bash

cd "$(dirname "$0")"

source config.sh

scp stop.sh start.sh restart.sh "$host:$deployPath"

rsync -aP ../target/scala-2.12/meta-assembly-0.3.3.jar "$host:$deployPath"assembly.jar

ssh "$host" "$deployPath"restart.sh
