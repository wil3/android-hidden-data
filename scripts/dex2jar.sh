#!/bin/sh

# Helper script to convert apks to jars so they can be used with jd-gui or
# other tools. A directory will be created with the jar in the same location as the apk
#
# Usage
#  dex2jar.sh [apk directory]	

#First argument is the source directory of the apks to decompile
APK="$1/*.apk"
DEST=$1

#Change this ot where your dex2jar is
DEX2JAR=/home/wil/programs/dex2jar-2.0/d2j-dex2jar.sh 


for f in $APK
do
	#From https://stackoverflow.com/questions/2664740/extract-file-basename-without-path-and-extension-in-bash
	APKFILENAME=${f##*/}
	DIRNAME=${APKFILENAME%.apk}
	#echo $NAME
	JAR="$DEST/$DIRNAME/$DIRNAME.jar"
	SRC="$DEST/$DIRNAME/"
	#mkdir -p $D
	$DEX2JAR $f -o $JAR --force

done
