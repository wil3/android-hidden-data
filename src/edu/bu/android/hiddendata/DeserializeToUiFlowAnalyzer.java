package edu.bu.android.hiddendata;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import edu.bu.android.hiddendata.model.DeserializeToUIConfig;
import edu.bu.android.hiddendata.model.JsonUtils;
import soot.Unit;
import soot.ValueBox;
import soot.jimple.AssignStmt;
import soot.jimple.InvokeExpr;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;

/**
 * 
 * @author William Koch
 *
 */
public class DeserializeToUiFlowAnalyzer extends FlowAnalyzer {
	private static final Logger logger = LoggerFactory.getLogger(DeserializeToUiFlowAnalyzer.class.getName());

	private InfoflowResults results;
	private DeserializeToUIConfig modelResults;
	/**
	 * 
	 * @param context The application context
	 * @param results The results from the flow analsysi from the network to deserialize methods
	 */
	public DeserializeToUiFlowAnalyzer(File resultsFile , SetupApplication context, InfoflowResults results){
		super(context);
		this.results = results;
		
		//Obtained from the first pass where all the models are found
		this.modelResults = JsonUtils.loadFirstPassResultFile(resultsFile);
	}
	
	public List<String> findHiddenData(){
		
		List<String> hiddenValues = new ArrayList<String>();
		
		if (results != null){
			Set<String> foundSources = new HashSet<String>();

			/*
			 * There are currently 4 different types of flows
			 * 
			 * fromJson -> UI element 	If this is found nothing more is needed
			 * 
			 * List()   -> UI element	
			 * fromJson -> List.add()	Is this list that is added the same as the list who has a path to a UI element?
			 */
			//Just add all the signatures of the sources into a list
			for (ResultSinkInfo foundSink : results.getResults().keySet()) {
				
				
				for (ResultSourceInfo foundSource : results.getResults().get(foundSink)) {
					
					//Make sure its form original source
					if (isOriginalSource(foundSource.getSource().toString())){
					
						//TODO need to filter out possible false positives from List of model not derived from network
						//if (isDeserializeToUIFlow(foundSink, foundSource)){
							//Once we have the 
							Stmt stmt = foundSource.getSource();
							//foundSources.add(stmt.toString());
							foundSources.addAll(getTaintedModelMethodFromFlow(foundSource));
						//} else {
							
						//}
					//Make sure its form original source
					//if (isOriginalSource(foundJsonSources.getSource().toString())){
						
					//}
					}
				}
			}
			
			//From all of the method signatures, remove the ones we found
			//whats left is hidden
			for (String f : foundSources ){
				logger.info("Method used! {}", f);
				modelResults.getGetMethodSignatures().remove(f);
			}
			
		}
		for (String hidden : modelResults.getGetMethodSignatures()){
			logger.info("HIDDEN {}", hidden);
		}
		
		return hiddenValues;
	}
	
	
	/**
	 * When we find a flow we must find out which method from the model was tainted and 
	 * ended up at the sink. This will identify data that is displayed to the user.
	 * @return Return a list because we want to find all the get methods that may have been accessed to get to this sink
	 */
	private Set<String> getTaintedModelMethodFromFlow(ResultSourceInfo source){
		
		
		Set<String> tainted = new HashSet<String>();
		Collections.reverse(source.getPath());
		for (Stmt stmt : source.getPath()){
			String signature = null;
			if (stmt instanceof AssignStmt){
				AssignStmt assignStmt = (AssignStmt) stmt;
				if (assignStmt.containsInvokeExpr()){
					signature = assignStmt.getInvokeExpr().getMethod().getSignature();
				}
			} else if (stmt instanceof InvokeStmt){
				signature = ((InvokeStmt)stmt).getInvokeExpr().getMethod().getSignature();
			} else {
				
			}
			
			if (signature != null){
				if (modelResults.getGetMethodSignatures().contains(signature)){
					//return signature;
					tainted.add(signature);
				}
			}
		}
		return tainted;
	}
	/**
	 * Because of the way the flows are reported there could be errors from parsing so dont do that, 
	 * just see if the signature is present in any sources found.
	 * 
	 * @param foundSources	List of all the sources found in the analysis
	 * @param source	A source from the model
	 * @return
	 */
	private boolean found(List<String> foundSources, String source){
		for (String foundSource : foundSources){
			if (foundSource.contains(source)){
				return true;
			}
		}
		return false;
	}
	
	
	private void displayResults(HashMap<String, Integer> references, List<String> hiddenValues){
		logger.info("All references found in code:");
		//logger.info("\t{}", references);
		logger.info("These appear to be hidden from user (references in app). If no references found in app then (1) sent to phone but never used, (2) not used at all.");
		for (String signature : hiddenValues){
			int referencesInApp = references.get(signature);
			logger.info("\t({}) {} ", referencesInApp, signature );
		}
		
	}
	
}
