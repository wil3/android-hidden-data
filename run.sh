#!/bin/bash

#Let each analysis run for an hour


APKS=/home/wfkoch/apks/pass1
java -d64 -XX:+UseConcMarkSweepGC -Xmx160g -Xms160g -Dorg.slf4j.simpleLogger.defaultLogLevel=info -cp .:lib/* edu.bu.android.hiddendata.FindHidden $APKS ~/libs/android-sdk-linux/platforms  --pathalgo contextsensitive --fragments --aplength 5 --SYSTIMEOUT 1800 --layoutmode none --sourcessinks SourcesAndSinks_1.txt --CGALGO SPARK
