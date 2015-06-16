package edu.bu.android.hiddendata;

import java.util.Iterator;

import soot.RefType;
import soot.Type;
import soot.Value;
import soot.ValueBox;
import soot.jimple.InvokeStmt;
import soot.jimple.Stmt;
import soot.jimple.StringConstant;
import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.source.data.SourceSinkDefinition;
import soot.jimple.infoflow.results.ResultInfo;
import soot.jimple.infoflow.results.ResultSinkInfo;
import soot.jimple.infoflow.results.ResultSourceInfo;
import soot.jimple.internal.JInterfaceInvokeExpr;
import soot.jimple.internal.JimpleLocal;

public abstract class FlowAnalyzer {
	protected SetupApplication context;

	public FlowAnalyzer(SetupApplication context) {
		this.context = context;
		soot.G.reset();
		context.initializeSoot();

	}

	protected String extractClassFromListAdd(Stmt sink){
		return extractModelFromInvokeStmt((InvokeStmt)sink);
	}
	public static  boolean isAndroidFramework(String className){
		return className.startsWith("java.") || 
				className.startsWith("android.") || 
				className.startsWith("javax.") ||
				className.startsWith("org.apache.http") ||
				className.startsWith("org.json") ||
				className.startsWith("org.w3c") ||
				className.startsWith("org.xml") ||
				className.startsWith("org.xmlpull") ||
				className.startsWith("com.android") ||
				className.startsWith("dalvik.") ||

				//not really
				className.startsWith("com.google.gson");


	}
	
	protected String extractModelFromInvokeStmt(InvokeStmt stmt){

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
	
	//TODO the source can be any implementation of a List, add this also to sourcesink file
	protected boolean isListFlow(ResultSinkInfo sink, ResultSourceInfo source){
		return (sink.getSink().toString().contains("<java.util.List: boolean add(java.lang.Object)>")
				|| sink.getSink().toString().contains("<java.util.List: boolean addAll(java.util.Collection)>"))
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
