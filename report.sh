#!/bin/bash

OUTPUT=/data/results_fromjson

java -cp .:lib/* edu.bu.android.hiddendata.BatchResultReporter --results=$OUTPUT  -s -l -m -t