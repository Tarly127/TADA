#!/bin/bash

# function to calculate the cpu usage of a process, given its PID
# 2nd parameter is the time from which we want to start evaluating cpu usage.
function calc_cpu_usage()
{
  MY_PID=$1
  #START=$2
  # shellcheck disable=SC2207
  PROCESS_STAT=($(sed -E 's/\([^)]+\)/X/' "/proc/$MY_PID/stat"))
  PROCESS_UTIME=${PROCESS_STAT[13]}
  PROCESS_STIME=${PROCESS_STAT[14]}
  PROCESS_STARTTIME=${PROCESS_STAT[21]}
  SYSTEM_UPTIME_SEC=$(tr . ' ' </proc/uptime | awk '{print $1}')
  CLK_TCK=$(getconf CLK_TCK)

  # shellcheck disable=SC2219
  let PROCESS_UTIME_SEC="$PROCESS_UTIME / $CLK_TCK"
  let PROCESS_STIME_SEC="$PROCESS_STIME / $CLK_TCK"
  let PROCESS_STARTTIME_SEC="$PROCESS_STARTTIME / $CLK_TCK"

  let PROCESS_ELAPSED_SEC="$SYSTEM_UPTIME_SEC - $PROCESS_STARTTIME_SEC"
  let PROCESS_USAGE_SEC="$PROCESS_UTIME_SEC + $PROCESS_STIME_SEC"
  let PROCESS_USAGE="$PROCESS_USAGE_SEC * 100 / $PROCESS_ELAPSED_SEC"

  # shellcheck disable=SC2027
  echo ${MY_PID}";"${PROCESS_UTIME_SEC}";"${PROCESS_STIME_SEC}";"${PROCESS_ELAPSED_SEC}";"${PROCESS_USAGE}
}

# shellcheck disable=SC2207
# shellcheck disable=SC2009
IFS=$'\n' process_array=($(ps -la | grep java))

echo "PID;utime;stime;elapsed;cpu_usage"

for process in "${process_array[@]}";
do
  if [[ ${process} != *"grep"* ]];
  then
    # get PID (2nd element)
    # shellcheck disable=SC2206
    IFS=$' ' PID=(${process})
    # calculate cpu usage
    calc_cpu_usage ${PID[3]} #${1}
  fi
done

