#Running

When running the analysis a directory sources-sinks will be created containing the SourceAndSinks file for the first pass of the analysis (Network to model deserialization).

#Build

```
mvn install
```

#Assemble

```
mvn assembly:assembly
```

#Files

SourcesAndSinks_init.txt
Sinks_ui.txt
sources-sinks/SourcesAndSinks_<apk name>.txt
EasyTaintWrapperSource-bootstrap.txt
parameter-index-lookup.json


#Coverage
##ListViews and Adapters
* Model is extracted from the fromJson method
* All constructors of Lists of this model type are located (by first finding flows from List constructor to add method) and then a model
is added to the list to force taint of the list
* The model is also added as a source
* If it is reported that a flow is found from this source AND fromJson then check to see if they have the same source. If so we can infer that the list was used but can extract the object params from the flow with the model
* When model source is tainted it will also allow for the path to be extracted.

#Deserializing

##Gson

Take from [https://sites.google.com/site/gson/gson-user-guide](https://sites.google.com/site/gson/gson-user-guide)

* It is perfectly fine (and recommended) to use private fields
There is no need to use any annotations to indicate a field is to be included for serialization and deserialization. All fields in the current class (and from all super classes) are included by default.
* If a field is marked transient, (by default) it is ignored and not included in the JSON serialization or deserialization.
* This implementation handles nulls correctly
* While serialization, a null field is skipped from the output
* While deserialization, a missing entry in JSON results in setting the corresponding field in the object to null
* If a field is synthetic, it is ignored and not included in JSON serialization or deserialization
* Fields corresponding to the outer classes in  inner classes, anonymous classes, and local classes are ignored and not included in serialization or deserialization



#Known Issues

* If parameter is refType we can not identify the varaible reference

This could possibly be fixed by modifying Infoflow.java line 583


#TODO 

* If we can find all instances of the model, can we set those as sources at the locations found to see if they flow there?
* Top 1M apps with network permissions, find out
(a) what HTTP library they are using
(b) What JSON library

* Figure out how to taint the rsponse from fromJson and all its methods
When a sink is found need to know the class and line number it is in 

On second pass need to be able to specify additionall informatino to be read by source and sink file to precicely location the sink

There is a JCastExpr value, the assignment is what needs to be tainted. This must be located first


* 6-11-15 What happens if a model returns a list?
* What about other things other than lists, need hashmap, sets, etc
#Coverage
* Async tasks (yes)
* Fragments (yes)
* Handlers?
* Base adapter? It looks like this is missing from call graph
* Threads?
* Adapters? Use setadapter as a sink?
	Play with getting at least the adapter part working, then do preprocessing to get the class sig that extend base adapter
	Build inheritance graph possibly
	-It seems the getView of the adapter is called when the view is first displayed from onLayout
	and whenever the notifyDataSetChanged method is called which triggers the view to be recalculated?

*Generics
	If a model set type is Object then look for instances of it in the code and then extract the object


#UI element issues
* If defined doesnt mean its actually used. Does it come from findviewbyid


#Soot notes

The sinksource parser is soot.jimple.infoflow.android.data.parsers.PermissionMethodParser.java
The soot.jimple.infoflow.data.AccessPath.java class represents the taint

soot.jimple.infoflow.data.Abstraction tracks the taint

soot.jimple.infoflow.data.AccessPath is the taint
If using custom source and sink manager can use custom SourceInfo to hold additional information about a source by extending soot.jimple.infoflow.source.SourceInfo


Some UI elements are listed as Source/Sink (Bundle, Menu, View..) because it thinks its a callback, why?
When callback is enabled and aggressivetw is not used we get many false positives. It reports more flows due to the extra sources/sinks added by the callbacks. I thnk this is for seeding so you can find flows in these callbacks



#Troubleshooting

when aggressivetaint enabled, fromJson is never called by soot.jimple.infoflow.problems.InfoflowProblem.computeWrapperTaints()

when aggressivetaint enabled computeTargetsInternal source == getZeroValue() causing a taint to propagaint but when not aggressive there is an extra for textview


EasyTaintWrapper getTaintsForMethodInternal
taintedPath = $r4(com.github.wil3.android.flowtests.IP) *

EasyTaintTwrapper isExclude supposely determines if you can propagate inside the callee



#Tests
Test-flow app will find a flow when not aggressive, no callbacks,  nothing added to easytaint

## No aggressive, Callbacks enabled
fromJson is not a source for a flow found, is it because of the tainting for the cast?
If source is IP ip = new IP() the flow is found suggesting the problem is the casting which is done when in aggressive mode


So if we taint the List by injecting an add of the model type, we can then get paths to the sink and we just have to check for the source that is fromJson which would mean that that tainted List actually does flow to the UI sink

We also need the Model constructor to be a source which is how the list becomes tainted

#Generics and Type Parameters

* If model extends class with generics, look at parent class and replace any return values with type defined by child
* look through extended generic path and collect any non android|java class add those to super class list


#Checklist before leaving
*Get pageadapter working - FIXED page adapter was not part of call graph
*get addall working - FIXED
*optimize validation class - Done
* write results to file - Done
* return types of generic lists
* Problem with agnostic results
