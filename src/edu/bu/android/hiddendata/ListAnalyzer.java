package edu.bu.android.hiddendata;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import edu.bu.android.hiddendata.FlowAnalyzer.ListFlow;
import edu.bu.android.hiddendata.model.DeserializeToUIConfig;
import edu.bu.android.hiddendata.model.InjectionPoint;
import edu.bu.android.hiddendata.model.JsonUtils;
import soot.SootClass;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;

public class ListAnalyzer extends FlowAnalyzer {
	private InfoflowResults results;
	NetworkToDeserializeFlowAnalyzer pass1;
	private DeserializeToUIConfig modelResults;
	private File resultsFile;
	public ListAnalyzer(SetupApplication context,  InfoflowResults results, NetworkToDeserializeFlowAnalyzer pass1, File resultsFile ) {
		super(context);
		this.results = results;
		this.resultsFile = resultsFile;
		this.pass1 = pass1;
		this.modelResults = JsonUtils.loadFirstPassResultFile(resultsFile);

	}

	public void process(){
		if (results == null){
			return;
		}
		
		//Track all the objects that are added to lists
		HashMap<String, ListFlow> addMethodParameterClassNames = new HashMap<String, ListFlow>();
		
		for (ResultSinkInfo sink : results.getResults().keySet()) {
			
			//Look at the sources and see which are actually specified in our source sink file
			//so we can filter out possible onces found from the callbacks enabled
			for (ResultSourceInfo source : results.getResults().get(sink)) {
				
				//Make sure its form original source
				if (isOriginalSource(source.getSource().toString())){

						ListFlow listFlow = new ListFlow();
						listFlow.source = source;
						listFlow.sink = sink;
						
						String sig = getSignature(sink.getDeclaringClass(), sink.getSink());
						String modelClass = pass1.getModelToAddSignatureMapping().get(sig);
						addMethodParameterClassNames.put(modelClass, listFlow);
				
				}
			}
		}
		
		
		Set<InjectionPoint> injections = new HashSet<InjectionPoint>();
		Iterator<String> it = addMethodParameterClassNames.keySet().iterator();
		while(it.hasNext()){
			String addMethodParameterClassName = it.next();
			ListFlow resultInfo = addMethodParameterClassNames.get(addMethodParameterClassName);
			
			//Right after a List constructor
			//Inject list.add(new Model())
			//TODO need to add line number to differentiat between mutlple targets
			InjectionPoint inject = new InjectionPoint();
			inject.setDeclaredClass(resultInfo.source.getDeclaringClass().getName());
			inject.setTargetInstruction(resultInfo.source.getStmt().toString());
			inject.setMethodSignature(resultInfo.source.getMethod().getSubSignature());
			inject.setClassNameToInject(addMethodParameterClassName);
			injections.add(inject);
		
		}
		
		modelResults.setInjections(injections);
		JsonUtils.writeResults(resultsFile, modelResults);
		
	}
	
	private String getSignature(SootClass sc, Stmt stmt){
		AndroidMethod am = new AndroidMethod(stmt.getInvokeExpr().getMethod());
		am.setLineNumber(stmt.getJavaSourceStartLineNumber());
		am.setDeclaredClass(sc.getName());
		String signatureWithLineNumber = am.getSignature();
		return signatureWithLineNumber;
	}
}
