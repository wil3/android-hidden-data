#!/bin/bash

# Script to build the soot libraries and deploy to local repo
# Change the following variables for your own machine

if [ $# -eq 0 ]
  then
    echo "Please specify the endpoint to deploy in format <user>:<ip address>:<remote path>"
fi

ENDPOINT=$1


#
# Replace this location with workspace
WORKSPACE=/home/wil/workspace


REPO=file://$WORKSPACE/android-hidden-data/repo/
SOOT_INFOFLOW_JAR=$WORKSPACE/soot-infoflow/build/soot-infoflow.jar
SOOT_INFOFLOW_ANDROID_JAR=$WORKSPACE/soot-infoflow-android/build/soot-infoflow-android.jar

#Build infoflow
cd $WORKSPACE/soot-infoflow/
ant -buildfile build.xml clean compile jar

#Build infoflow-android
cd $WORKSPACE/soot-infoflow-android/
ant -buildfile build.xml clean compile jar

#Re-deploy the infoflow jars
mvn deploy:deploy-file -Durl=$REPO -Dfile=$SOOT_INFOFLOW_JAR -DgroupId=edu.bu.android_mitm -DartifactId=soot-infoflow -Dpackaging=jar -Dversion=1.0-16062015
mvn deploy:deploy-file -Durl=$REPO -Dfile=$SOOT_INFOFLOW_ANDROID_JAR -DgroupId=edu.bu.android_mitm -DartifactId=soot-infoflow-android -Dpackaging=jar -Dversion=1.0-16062015

#Fully build
cd $WORKSPACE/android-hidden-data/
mvn clean assembly:assembly

#Get current version
VERSION=$(mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version | grep -v '\[')
#Finally push to server
scp $WORKSPACE/android-hidden-data/target/android-data-received-$VERSION-distribution.zip $ENDPOINT/android-data-received-$VERSION-distribution.zip
