package edu.bu.android.hiddendata;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Unit;
import soot.ValueBox;
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
public class ProcessDeserializeToUiResults {
	private static final Logger logger = LoggerFactory.getLogger(ProcessDeserializeToUiResults.class.getName());

	private InfoflowResults results;
	private Set<SourceSinkDefinition> jsonSources;
	private SetupApplication context;
	
	/**
	 * 
	 * @param context The application context
	 * @param results The results from the flow analsysi from the network to deserialize methods
	 */
	public ProcessDeserializeToUiResults(SetupApplication context, InfoflowResults results){
		this.results = results;
		this.jsonSources = context.getSources();
		this.context = context;
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
	
	public List<String> process(){
		
		List<String> hiddenValues = new ArrayList<String>();
		
		List<String> foundSources = new ArrayList<String>();
		if (results != null){
		for (ResultSinkInfo sink : results.getResults().keySet()) {
			for (ResultSourceInfo foundJsonSources : results.getResults().get(sink)) {
				Stmt stmt = foundJsonSources.getSource();
				foundSources.add(stmt.toString());
			}
		}
		}
		
		Iterator<SourceSinkDefinition> it = jsonSources.iterator();
		List<String> jsonSourceList = new ArrayList<String>();
		while (it.hasNext()){
			SourceSinkDefinition def = it.next();
			String signature = def.toString();
			jsonSourceList.add(signature);
			if (!found(foundSources, signature)){
				hiddenValues.add(signature);
			}
		}
		
		for (String h : hiddenValues){
			logger.info("HIDDEN " + h);
		}
		
		//Now look throughout the application and find the actual uses
		ValidateFlows val = new ValidateFlows(context, jsonSourceList);
		val.validate();
		
		HashMap<String, Integer> references = val.getResults();
		
		displayResults(references, hiddenValues);
		return hiddenValues;
	}
	
	
	private void displayResults(HashMap<String, Integer> references, List<String> hiddenValues){
		logger.info("All references found in code:");
		logger.info("\t{}", references);
		logger.info("These appear to be hidden from user:");
		for (String h : hiddenValues){
			logger.info("\t{}", h);
		}
		
	}
}
