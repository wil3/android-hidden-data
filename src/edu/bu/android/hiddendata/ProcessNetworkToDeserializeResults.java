package edu.bu.android.hiddendata;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.Unit;
import soot.Value;
import soot.ValueBox;
import soot.jimple.AbstractStmtSwitch;
import soot.jimple.AssignStmt;
import soot.jimple.ClassConstant;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.internal.AbstractInvokeExpr;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JimpleLocal;

/**
 * Process the results of the flow analysis from network connections to JSON deserialization.
 * 
 * Find the model object and write all get methods to a new sink file to run as a second pass.
 * 
 * @author Wil Koch
 *
 */
public class ProcessNetworkToDeserializeResults {

	//private final Hashtable<String, ObjectExtractionQuery> extractionPoints;
	private final Hashtable<String,Integer> modelParameterIndexLookup = new Hashtable<String, Integer>();
	private static final Logger logger = LoggerFactory.getLogger(ProcessNetworkToDeserializeResults.class.getName());
	InfoflowResults results;
	String apkFileName;
	File sourcesAndSinksFile;
	public ProcessNetworkToDeserializeResults(File sourcesAndSinksFile, InfoflowResults results){
		this.results = results;
		this.sourcesAndSinksFile = sourcesAndSinksFile;
		
		//TODO stick in config file
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(java.lang.String,java.lang.Class)>", 1);
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(com.google.gson.JsonElement,java.lang.Class)>", 1);
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(java.io.Reader,java.lang.Class)>", 1);
	}
	
	
	private String extractModelFromInvokeStmt(InvokeStmt stmt){

		 Value v = stmt.getInvokeExprBox().getValue();
		 if (v instanceof JInterfaceInvokeExpr){
			 JInterfaceInvokeExpr jv = (JInterfaceInvokeExpr) v;

			 if (jv.getMethodRef().getSignature().equals("<java.util.Map: java.lang.Object put(java.lang.Object,java.lang.Object)>")){

				 if (jv.getBaseBox().getValue() instanceof JimpleLocal){
					 JimpleLocal local = (JimpleLocal)jv.getBaseBox().getValue();
					 String localName = local.getName();
					 for (int i=0; i<jv.getArgCount(); i++){
						 ValueBox ab = jv.getArgBox(i);
						 if (ab.getValue() instanceof StringConstant){
							
						 }
					 }
				 }
			 }
		 }
		 
		 return null;
	}
	
	private String extractModelFromAssignStmt(AssignStmt stmt){
		String className = null;

		Value v = stmt.getRightOpBox().getValue();
		 if (v instanceof AbstractInvokeExpr){
			 AbstractInvokeExpr aie = (AbstractInvokeExpr)v;
			 String sig = aie.getMethodRef().getSignature();
			 logger.debug("Signature " + sig);
			 
			 if (modelParameterIndexLookup.containsKey(sig)){

				 int parameterIndex = modelParameterIndexLookup.get(sig);
				 List<ValueBox> boxes = v.getUseBoxes(); //get from here
				 
				 ValueBox vb = boxes.get(parameterIndex);
				 
				 if (vb != null){
					Value boxVal = vb.getValue();
					if (boxVal instanceof JimpleLocal){
						String localName = ((JimpleLocal) boxVal).getName();
						 Type argType = vb.getValue().getType(); //Is this the class or a reference?
						 className = argType.toString();
						 if (className.equals("java.util.Map")){
						
						 }
					

					} else if (boxVal instanceof ClassConstant){
						
						ClassConstant rt = (ClassConstant) boxVal;									
						className = convertClassPathToClassName(rt.getValue());

					} else {
						logger.error("Fuck if I know");
					}
					
					
					
				 }
			 }
			 
		 }
		 return className;
	}
	
	private String convertClassPathToClassName(String classPath){
		return classPath.replace("/", ".");
	}
	
	
	private List<String> getSourcesFromSootClassModel(SootClass model){
		
		List<String> sources  = new ArrayList<String>();
		for (SootMethod method : model.getMethods()){
			String methodName = method.getName();
			if (shouldAcceptMethod(methodName)){
				sources.add(method.getSignature());
			}
		}
		
		return sources;
	}
	
	private boolean shouldAcceptMethod(String methodName){
		return methodName.toLowerCase().startsWith("get") || 
				methodName.toLowerCase().startsWith("is");
		
	}
	
	
	private SootClass extractSootClassModel(Stmt stmt){
		
		String className = null;

		
		if (stmt instanceof InvokeStmt){
			className = extractModelFromInvokeStmt((InvokeStmt) stmt);

		} else if (stmt instanceof AssignStmt){
			className = extractModelFromAssignStmt((AssignStmt) stmt);
		}

		if (className != null){
			return Scene.v().getSootClass(className);
		}
		
		return null;
	}
	
	
	public void process(){
		if (results == null){
			return;
		}
		
		List<String> modelClassNames = new ArrayList<String>();
		
		for (ResultSinkInfo sink : results.getResults().keySet()) {
			Stmt stmt = sink.getSink();
			SootClass model = extractSootClassModel(stmt);
			
			//Make sure were only processing one because we might have multiple sources to this sink
			if (!modelClassNames.contains(model.getName())){
				List<String> sources = getSourcesFromSootClassModel(model);
				dumpSourcesToFile(sources);
				modelClassNames.add(model.getName());
				
			}
		}
	}
	
	private void dumpSourcesToFile(List<String> sources){
		
		Path path = FileSystems.getDefault().getPath("./Sinks_ui.txt");
		
		
		PrintWriter writer = null;
		try {
			
			writer = new PrintWriter(sourcesAndSinksFile, "UTF-8");
			for (String source : sources){
				logger.debug("Next source " + source);
				//We are reverseing this because in infoflow the layout mode specifies all the layout elements as sources
				String sourceEntry = source + " -> _SOURCE_";
				writer.println(sourceEntry);
			}
			
			writer.println("");
			writer.println("");

			List<String> sinks = Files.readAllLines(path, Charset.defaultCharset());

			//Now add all the sinks
			for (String sink : sinks){
				writer.println(sink);
			}
			
		} catch (IOException e){
			e.printStackTrace();
		} finally {
			if (writer != null){
				writer.close();
			}
		}
	}
}
