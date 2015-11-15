#!/bin/bash

#This is a helper script to dump important information about an apk

AAPT=/home/wfkoch/libs/android-sdk-linux/build-tools/22.0.1/aapt
FILES=/home/wfkoch/apks/gson_internet/apks/*
APK_HOME=/home/wfkoch/apks/large/

package_regex="package: name='([a-zA-Z0-9._]*)'"
name_regex="application-label:'([^']*)'"

while IFS='' read -r line || [[ -n $line ]]; do
	OUT=$($AAPT dump badging "$APK_HOME$line" 2> /dev/null) 
	[[ $OUT =~ $package_regex ]]
	package="${BASH_REMATCH[1]}"

	[[ $OUT =~ $name_regex ]]
	name="${BASH_REMATCH[1]}"
	
	echo "$line, $name, $package"
	#echo $OUT
done < "$1"
