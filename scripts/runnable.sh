#!/bin/bash

# 
# Args: [file of apks]
#
 
AAPT=/home/wfkoch/libs/android-sdk-linux/build-tools/22.0.1/aapt

package_regex="package: name='([a-zA-Z0-9._]*)'"
name_regex="application-label:'([^']*)'"
locale_regex="locales: ([\ a-zA-Z_'-]+)"
while IFS='' read -r apk || [[ -n $apk ]]; do
	OUT=$($AAPT dump badging "$apk" 2> /dev/null) 
	[[ $OUT =~ $package_regex ]]
	package="${BASH_REMATCH[1]}"

	[[ $OUT =~ $name_regex ]]
	name="${BASH_REMATCH[1]}"

	#Check if english by the locals
	#[[ $OUT =~ $locale_regex ]]
	#locale="${BASH_REMATCH[1]}"

	#Grab ones supporting english
	country=$(echo $OUT | grep locales | grep "'en")	
	if [[ $country ]]; then

		#Check if exists in play store
		res=$(curl https://play.google.com/store/apps/details?id=$package -ISs | grep "HTTP/1.1 200 OK")
		if [[ $res ]]; then
			echo "$apk"
		fi
	fi
 
	sleep 1 #rate limit

done < "$1"


