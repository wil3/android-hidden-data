package edu.bu.android.hiddendata;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Body;
import soot.BodyTransformer;
import soot.Main;
import soot.PackManager;
import soot.PatchingChain;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Transform;
import soot.Unit;
import soot.jimple.infoflow.IInfoflow.CallgraphAlgorithm;
import soot.jimple.infoflow.android.SetupApplication;
import soot.options.Options;
import soot.util.Chain;

/**
 * Search for all references of the deserialize model object so it can be used as validation if the flows are found.
 * 
 * This will help identify possible flows that are missed. 
 * 
 * @author William Koch
 */
public class ValidateFlows {
	private static final Logger logger = LoggerFactory.getLogger(ValidateFlows.class.getName());


	List<String> methodsToSearchFor;
	SetupApplication context;
	
	HashMap<String, Integer> methodHistogram = new HashMap<String, Integer>();
	
	public ValidateFlows(SetupApplication context, List<String> methodsToSearchFor){
		this.methodsToSearchFor = methodsToSearchFor;
		this.context = context;
		initMethodHistogram();

	}
	
	private void initMethodHistogram(){
		for (String method : methodsToSearchFor){
			methodHistogram.put(method, 0);
		}
	}
	
	public void validate(){
		soot.G.reset();

		context.initializeSoot();
		
		
	        PackManager.v().getPack("jtp").add(new Transform("jtp.myInstrumenter", new BodyTransformer() {

				@Override
				protected void internalTransform(final Body b, String phaseName, @SuppressWarnings("rawtypes") Map options) {

						processBody(b);
					}
			}));
			
	        PackManager.v().runPacks();
	}
	
	public void validate2() {
		soot.G.reset();

		context.initializeSoot();
		
		Chain<SootClass> classes = Scene.v().getApplicationClasses();
		Iterator<SootClass> it = classes.iterator();
		while (it.hasNext()){
			SootClass sootClass = it.next();
			try {
			searchForMethod(sootClass);
			} catch(Exception e){
				e.printStackTrace();
			}
		}
	}
	
	public boolean usesSearchedForMethod(Unit unit){
		
		boolean found = false;
		for (String methodSig : methodsToSearchFor){
			if (unit.toString().contains(methodSig)){
				int c = methodHistogram.get(methodSig);
				methodHistogram.put(methodSig, c+1);
				logger.info("Found source in {} ", unit.toString());
				return true;
			}
		}
		return found;
	}
	
	private void searchForMethod(SootClass sootClass){
		for (SootMethod method : sootClass.getMethods()){
			
			Body body = method.retrieveActiveBody();
			processBody(body);
		}
	}
	
	private void processBody(Body body){
		Iterator<Unit>it =  body.getUnits().snapshotIterator();
		while(it.hasNext()){
			Unit unit = it.next();
			if (usesSearchedForMethod(unit)){
				
			}
		}
	}
	
	public HashMap<String, Integer> getResults(){
		return methodHistogram;
	}
	

}
