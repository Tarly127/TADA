#!/bin/sh

MAIN_CLASS=$2

LOG_DIR="logs/$USER/"

mkdir -p "$LOG_DIR"

mvn clean package

export MAVEN_OPTS="-server"
# aditional stuff to log GC
#export MAVEN_OPTS="-XX:+UseSerialGC -Xms1024m -Xmx1024m -verbose:gc -Xlog:gc* -Xlog:gc*::time -Xlog:gc:$(LOG_DIR)BSO_$1_jvm.log"

# init leader
mvn exec:java -Dexec.mainClass="${MAIN_CLASS}" -Dexec.args="12345 12345 $1" &

#unset MAVEN_OPTS

sleep 3
i=1
MAX=$(($1-1))

while [ $i -lt $MAX ]
do
    # shellcheck disable=SC2004
    address=$((12345+$i))
    mvn exec:java -Dexec.mainClass="${MAIN_CLASS}" -Dexec.args="$address 12345 $1" > ${LOG_DIR}${address}.log &
    sleep 2
    true $((i+=1))
done

address=$((12345+$1-1))

mvn exec:java -Dexec.mainClass="${MAIN_CLASS}" -Dexec.args="${address} 12345 $1" > ${LOG_DIR}${address}.log
