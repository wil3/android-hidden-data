package edu.bu.android.hiddendata;

import java.util.Iterator;

import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.results.ResultInfo;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;

public abstract class FlowAnalyzer {
	protected SetupApplication context;

	public FlowAnalyzer(SetupApplication context) {
		this.context = context;

	}

	
	//TODO the source can be any implementation of a List, add this also to sourcesink file
	protected boolean isListFlow(ResultSinkInfo sink, ResultSourceInfo source){
		return sink.getSink().toString().contains("<java.util.List: boolean add(java.lang.Object)>")
	&& 
				source.getSource().toString().contains("<java.util.ArrayList: void <init>()>");
	}
	
	protected boolean isDeserializeToUIFlow(ResultSinkInfo sink, ResultSourceInfo source){
		return sink.getSink().toString().contains("android.") && 
				source.getSource().toString().contains("fromJson");

	}
	protected boolean isDeserializeToListAddFlow(ResultSinkInfo sink, ResultSourceInfo source){
		return sink.getSink().toString().contains("<java.util.List: boolean add(java.lang.Object)>") && 
				source.getSource().toString().contains("fromJson");

	}
	
	protected boolean isListToUIFlow(ResultSinkInfo sink, ResultSourceInfo source){
		//TODO need a better check here
		return sink.getSink().toString().contains("android.") && 
				source.getSource().toString().contains("<java.util.ArrayList: void <init>()>");

	}
	
	
	/**
	 * Because callbacks add additoinal seeds make sure we are only looking at the originals
	 * @param stmtString
	 * @return
	 */
	protected boolean isOriginalSource(String stmtString){
		Iterator<SourceSinkDefinition> it = context.getSources().iterator();
		while (it.hasNext()){
			SourceSinkDefinition sourceDef = it.next();
			String subSig = sourceDef.getMethod().getSubSignature();
			if (stmtString.contains(subSig.toString())){
				return true;
			}
		}
		return false;
	}
	
	protected String convertBytecodeToJavaClassName(String classPath){
		//TODO this wont work for all cases
		if (classPath.endsWith(";")){
			classPath = classPath.substring(classPath.indexOf("L") + 1, classPath.length() -1);
		}
		return classPath.replace("/", ".");
	}

	public class ListFlow {
		public ResultInfo source;
		public ResultInfo sink;
	}
}
