#!/bin/bash

#Let each analysis run for an hour

LOG_LEVEL=info
APKS=/home/wfkoch/apks/large
OUTPUT=/data/results
ANDROID_PLATFORMS=/home/wfkoch/libs/android-sdk-linux/platforms
java -d64 -XX:+UseConcMarkSweepGC -Xmx160g -Xms160g -Dorg.slf4j.simpleLogger.defaultLogLevel=$LOG_LEVEL -cp .:lib/* edu.bu.android.hiddendata.FindHidden $APKS $ANDROID_PLATFORMS  --pathalgo contextsensitive --fragments --aplength 5 --SYSTIMEOUT 1800 --layoutmode none --sourcessinks SourcesAndSinks_1.txt --CGALGO SPARK --output $OUTPUT
