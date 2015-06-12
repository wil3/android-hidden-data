package edu.bu.android.hiddendata;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import edu.bu.android.hiddendata.ModelExtraction.OnExtractionHandler;
import edu.bu.android.hiddendata.model.FirstPassResult;
import edu.bu.android.hiddendata.model.InjectionPoint;
import edu.bu.android.hiddendata.model.Model;
import soot.RefType;
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
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.data.AndroidMethod;
import soot.jimple.infoflow.results.InfoflowResults;
import soot.jimple.infoflow.results.ResultInfo;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
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
public class NetworkToDeserializeFlowAnalyzer extends FlowAnalyzer {

	//private final Hashtable<String, ObjectExtractionQuery> extractionPoints;
	private final Hashtable<String,Integer> modelParameterIndexLookup = new Hashtable<String, Integer>();
	private static final Logger logger = LoggerFactory.getLogger(NetworkToDeserializeFlowAnalyzer.class.getName());

	private InfoflowResults results;
	private final String apkFileName;
	private File sourcesAndSinksFile;
	private File easyTaintWrapperFile;
	private File resultsFile;
	
	
	public NetworkToDeserializeFlowAnalyzer(SetupApplication context, File sourcesAndSinksFile, InfoflowResults results){
		super(context);
		this.apkFileName = new File(context.getApkFileLocation()).getName();
		this.results = results;
		this.sourcesAndSinksFile = sourcesAndSinksFile;
		this.easyTaintWrapperFile = new File(sourcesAndSinksFile.getParentFile(), apkFileName + FindHidden.EASY_TAINT_WRAPPER_FILE_PREFIX);
		this.resultsFile = new File(sourcesAndSinksFile.getParentFile(),apkFileName + FindHidden.RESULTS_SUFFIX );
		//TODO stick in config file
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(java.lang.String,java.lang.Class)>", 1);
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(com.google.gson.JsonElement,java.lang.Class)>", 1);
		modelParameterIndexLookup.put("<com.google.gson.Gson: java.lang.Object fromJson(java.io.Reader,java.lang.Class)>", 1);
		modelParameterIndexLookup.put("<java.util.List: boolean add(java.lang.Object)>", 0);
	}
	
	
	/**
	 * Process the results of the flow to extract the models from the json deserilize method
	 */
	public void process(){
		if (results == null){
			return;
		}
		
		//Track all the objects that are added to lists
		HashMap<String, ListFlow> addMethodParameterClassNames = new HashMap<String, ListFlow>();
		Set<String> modelClassNames = new HashSet<String>();

		List<String> sourceSignatures = new ArrayList<String>();
		
		for (ResultSinkInfo sink : results.getResults().keySet()) {
			Stmt sinkStmt = sink.getSink();
			
			int type = 0;
			//Look at the sources and see which are actually specified in our source sink file
			//so we can filter out possible onces found from the callbacks enabled
			for (ResultSourceInfo source : results.getResults().get(sink)) {
				
				//Make sure its form original source
				if (isOriginalSource(source.getSource().toString())){
					
					if (isListFlow(sink, source)){
						ListFlow listFlow = new ListFlow();
						listFlow.source = source;
						listFlow.sink = sink;
						String addClassName = extractClassFromListAdd(sink.getSink());
						addMethodParameterClassNames.put(addClassName, listFlow);
						type = 1;
					} else { //for right now its actual network to deserialize flows
						type = 2;
					}
					
					//Only the sources are bizarre so break if you find one
//					break;
				}
			}
			
			//Is the sink a non-list flow?
			switch (type){
				case 2: {
				
					//Get the sink and add to file for next pass. We can use the entire method call
					//because soot will track taint of the casting
					
					sourceSignatures.add(makeSignature(sink));
					
					//Extract the model class from the deserialize method call so we can compare it 
					//to list objects
					modelClassNames.add(extractModelClassName(sinkStmt));
				}
				default: {
					//not sure yet
				}
			}
			
			
			
		}
		
		List<InjectionPoint> injections = new ArrayList<InjectionPoint>();
		List<String> sinkSignatures = new ArrayList<String>();
		//Now finish up
		//Look at all the objects extracted from List.add(java.lang.Object) and Model Objects
		//we want to use them in our next pass if they use the model objects
		
		//We are going to try to not do this and instead force taint where there is a list constructor
		
		Iterator<String> it = addMethodParameterClassNames.keySet().iterator();
		while(it.hasNext()){
			String addMethodParameterClassName = it.next();
			if (modelClassNames.contains(addMethodParameterClassName)){
				ListFlow resultInfo = addMethodParameterClassNames.get(addMethodParameterClassName);
				//it.remove();
				//sourceSignatures.add(makeSignature(resultInfo.source));
				//sinkSignatures.add(makeSignature(resultInfo.sink));
				
				
				//Get the origin List constructor location and save this so we can inject a constructor
				//in the next pass
				InjectionPoint inject = new InjectionPoint();
				inject.setDeclaredClass(resultInfo.source.getDeclaringClass().getName());
				inject.setTargetInstruction(resultInfo.source.getStmt().toString());
				inject.setMethodSignature(resultInfo.source.getMethod().getSubSignature());
				inject.setClassToInject(addMethodParameterClassName);
				injections.add(inject);
			}
		}
		
		
		FirstPassResult result = new FirstPassResult();
		List<Model> models = new ArrayList<Model>();
		final List<String> modelMethodSignatures = new ArrayList<String>();

		//Now that we have all the base models, analyze each
		//and get any other models they reference
		ModelExtraction me = new ModelExtraction();
		
		//For optimization since we already need to loop through all methods
		//also save all the get methods so we can on the second pass
		//use them to compare to what we found
		me.addHandler(new OnExtractionHandler() {
			
			@Override
			public void onMethodExtracted(SootClass sootClass, SootMethod method) {
				
				String methodName = method.getName();
				if (shouldAcceptMethod(methodName)){
					modelMethodSignatures.add(method.getSignature());

				}
				/*
				Type retType = method.getReturnType();
				//A list is a ref type as well
				if (retType instanceof RefType){
					RefType refType = (RefType) retType;
					String retClassName = refType.getClassName();
					if (!retClassName.startsWith("java.")){
					}
				}
				*/
			}
		});
		
		//Make the default constructors for the model classes found 
		//and also find all other associated models
		List<String> allModels = new ArrayList<String>();
		for (String model : modelClassNames){
			allModels.addAll(me.getModels(model));
			sourceSignatures.add(makeDefaultSignatureConstructor(model));
		}
		
		//Write out results to be used for next pass
		result.setGetMethodSignatures(modelMethodSignatures);
		result.setModelNames(allModels);
		result.setInjections(injections);
		writeResults(result);
		
		createEasyTaintWrapperFile(allModels);
		
		//Now create a new sink source file for the next pass
		createSinkSourceFile(sourceSignatures, sinkSignatures);
	
	}

	private String makeSignature(ResultInfo resultInfo){
		Stmt stmt = resultInfo.getStmt();
		String sinkClassName = resultInfo.getDeclaringClass().getName();
		int lineNumber = stmt.getJavaSourceStartLineNumber();
		AndroidMethod am = new AndroidMethod(stmt.getInvokeExpr().getMethod());
		am.setDeclaredClass(sinkClassName);
		am.setLineNumber(lineNumber);
		
		String sig = am.getSignature();
		return sig;
	}

	private String makeDefaultSignatureConstructor(String className){
		AndroidMethod am = new AndroidMethod("<init>", "void", className);
		return am.getSignature();
	}
	
	private String extractClassFromListAdd(Stmt sink){
		return extractModelFromInvokeStmt((InvokeStmt)sink);
	}
	
	
	private String extractModelFromInvokeStmt(InvokeStmt stmt){

		 Value v = stmt.getInvokeExprBox().getValue();
		 if (v instanceof JInterfaceInvokeExpr){
			 JInterfaceInvokeExpr jv = (JInterfaceInvokeExpr) v;

			 //This is old to support json with Maps
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
			 } else {
				 //Query for argument index
				 Type type = jv.getArgBox(0).getValue().getType();
				 if (type instanceof RefType){
					 RefType refType = (RefType) type;
					 return refType.getClassName();
				 }
			 }
		 }
		 
		 return null;
	}
	
	/**
	 * Parse out the model class name from the deserialization call
	 * @param stmt
	 * @return
	 */
	private String extractModelFromAssignStmt(AssignStmt stmt){
		String className = null;

		Value v = stmt.getRightOpBox().getValue();
		 if (v instanceof AbstractInvokeExpr){
			 
			 AbstractInvokeExpr invokeExpr = (AbstractInvokeExpr)v;
			 String sig = invokeExpr.getMethodRef().getSignature();
			 logger.debug("Signature " + sig);
			 if (modelParameterIndexLookup.containsKey(sig)){

				 int parameterIndex = modelParameterIndexLookup.get(sig);
				 				 
				 Value boxVal = invokeExpr.getArg(parameterIndex);
				 if (boxVal != null){
					//Value boxVal = vb.getValue();
					if (boxVal instanceof JimpleLocal){
						
						 Type argType = boxVal.getType(); //Is this the class or a reference?
						 String argTypeString = argType.toString();

						 if (argTypeString.equals("java.util.Map")){
						
						 } else if (argTypeString.equals("java.lang.Class")){
							 
						 } else {
							 className = argTypeString;
						 }
					} else if (boxVal instanceof ClassConstant){
						
						ClassConstant rt = (ClassConstant) boxVal;		
						className = convertBytecodeToJavaClassName(rt.getValue());

					} else {
						logger.error("Fuck if I know");
					}
				 }
			 }
			 
		 }
		 return className;
	}
	
	
	
	/**
	 * 
	 * @param baseClassName Never changes, the original class
	 * @param sootClass Class which is baseClassName or parent
	 * @param sources All signatures to be used for sources in next pass
	 * @return
	 */
	private List<String> getSourcesFromModel(String baseClassName, SootClass sootClass, List<String> sources){
		String className = sootClass.getName();
		if (className.equals("java.lang.Object")){
			return sources;
		} else {
			//Add all possible sources to the list
			sources.addAll(getSourcesFromSootClassModel(baseClassName, sootClass));
			
			if (!sootClass.hasSuperclass()){
				return sources;
			} else {
				//Check for parent methods
				SootClass superClass = sootClass.getSuperclass();
				return getSourcesFromModel(baseClassName, superClass, sources);
			}
		}
	}
	
	private List<String> getSourcesFromSootClassModel(String baseClassName, SootClass model){
		
		List<String> sources  = new ArrayList<String>();
		for (SootMethod method : model.getMethods()){
			String methodName = method.getName();
			if (shouldAcceptMethod(methodName)){
				String currentClass = method.getDeclaringClass().getName();
				
				//infoflow needs toplevel signature
				String sig = method.getSignature().replace(currentClass, baseClassName);
				sources.add(sig);
			}
		}
		
		return sources;
	}
	
	private boolean shouldAcceptMethod(String methodName){
		return methodName.toLowerCase().startsWith("get") || 
				methodName.toLowerCase().startsWith("is");
		
	}
	
	private String extractModelClassName(Stmt stmt){

		String className = null;
		if (stmt instanceof InvokeStmt){
			className = extractModelFromInvokeStmt((InvokeStmt) stmt);

		} else if (stmt instanceof AssignStmt){
			className = extractModelFromAssignStmt((AssignStmt) stmt);
		}

		return className;
	}
	
	private SootClass extractSootClassModel(Stmt stmt){
		String className = extractModelClassName(stmt);
		if (className != null){
			return Scene.v().getSootClass(className);
		}
		
		return null;
	}
	
	/**
	 * Create for the second pass so we can start independently
	 * 
	 * @param sources
	 * @param sinks
	 */
	private void createSinkSourceFile(List<String> sources, List<String> sinks){
		
		
		PrintWriter writer = null;
		try {
			
			writer = new PrintWriter(sourcesAndSinksFile, "UTF-8");
			for (String source : sources){
				String sourceEntry = source + " -> _SOURCE_";
				writer.println(sourceEntry);
			}
			writer.println("");
			for (String sink : sinks){
				String sinkEntry = sink + " -> _SINK_";
				writer.println(sinkEntry);
			}
			
			writer.println("");
			writer.println("");
			
			//Load all the known UI sinks
			Path path = FileSystems.getDefault().getPath("./Sinks_ui.txt");
			List<String> UISinks = Files.readAllLines(path, Charset.defaultCharset());

			//Now add all the sinks
			for (String sink : UISinks){
				writer.println(sink);
			}
			
		} catch (IOException e){
			logger.error(e.getMessage());
		} finally {
			if (writer != null){
				writer.close();
			}
		}
	}
	
	private void createEasyTaintWrapperFile(List<String> models){
		//Load all the known UI sinks
				
		PrintWriter writer = null;
		try {
			
			writer = new PrintWriter(easyTaintWrapperFile, "UTF-8");
			
			//Include the models
			for (String model : models){
				writer.println("^" + model);
			}
			
			
			Path path = FileSystems.getDefault().getPath("./EasyTaintWrapperSource-default.txt");
			List<String> easyTaints = Files.readAllLines(path, Charset.defaultCharset());

			//Now add all the defaults
			for (String taints : easyTaints){
				writer.println(taints);
			}
			
		} catch (IOException e){
			logger.error(e.getMessage());
		} finally {
			if (writer != null){
				writer.close();
			}
		}
	}
	
	/**
	 * Load the parameter mappings
	 * @return
	 */
	private Map loadParameterIndexLookupFile(){
		Map map = null;
		try {
			JsonReader reader = new JsonReader(new FileReader("jsonFile.json"));
			Gson gson = new Gson();
			map = gson.fromJson(reader, Map.class);
			
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		return map;
	}
	
	private void writeResults(FirstPassResult results){
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		PrintWriter writer = null;
		try {
				String json = gson.toJson(results);
			   writer = new PrintWriter(resultsFile, "UTF-8");
			   writer.write(json);
			  
			  } catch (IOException e) {
			   e.printStackTrace();
			  } finally{
				  if (writer != null){
					writer.close();
				  }
			  }
	}
	
	public File getEasyTaintWrapperFile() {
		return easyTaintWrapperFile;
	}


	public void setEasyTaintWrapperFile(File easyTaintWrapperFile) {
		this.easyTaintWrapperFile = easyTaintWrapperFile;
	}


}
