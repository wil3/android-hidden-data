#!/bin/bash

#Let each analysis run for an hour

LOG_LEVEL=info
APKS=/home/wfkoch/apks/fromjson_apps-v5.txt
OUTPUT=/data/results_fromjson
SINKSOURCE=ss-v5.txt
ANDROID_PLATFORMS=/home/wfkoch/libs/android-sdk-linux/platforms
java -d64 -XX:+UseConcMarkSweepGC -Xmx160g -Xms160g -Dorg.slf4j.simpleLogger.defaultLogLevel=$LOG_LEVEL -cp .:lib/* edu.bu.android.hiddendata.FindHidden $APKS $ANDROID_PLATFORMS  --pathalgo contextsensitive --fragments --aplength 5 --SYSTIMEOUT 1800 --layoutmode none --sourcessinks $SINKSOURCE --CGALGO SPARK --output $OUTPUT
