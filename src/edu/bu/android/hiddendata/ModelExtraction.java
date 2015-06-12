package edu.bu.android.hiddendata;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;

/**
 * For a given class name, extract all the super and return classes that are not of type java.lang.Object.
 * These will be used for easy taint wrapper so the taint can propagate through these classes without using aggressive mode.
 * 
 * @author Wil Koch
 *
 */
public class ModelExtraction {

	public interface OnExtractionHandler {
		public void onMethodExtracted(SootClass sootClass, SootMethod method);
	}
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private OnExtractionHandler handler;
	private String modelClassName;
	
	public ModelExtraction(){
	}
	
	public void addHandler(OnExtractionHandler handler){
		this.handler = handler;
	}
	/**
	 * Get all the models associated with this class including get methods that return other models
	 * and also from super classes.
	 * @return
	 */
	public List<String> getModels(String modelClassName){
		
		SootClass sootClass = Scene.v().getSootClass(modelClassName);
			
		//need to look at all the super methods too because although the child class can access them all
		//they are not represented in Java this way
		
		List<String> classes = new ArrayList<String>();
		getMethodReturnClassNames(sootClass, classes);
		
		for (String c : classes){
			logger.debug("Class {}", c);
		}
		return classes;
	}
	
	private void getMethodReturnClassNames(SootClass sootClass, List<String> classes){
		
		//For all the classes
		for (SootClass sc : getSuperSootClasses(sootClass)){
			classes.add(sc.getName());
			
			//And for all the methods
			for (SootMethod method : sc.getMethods()){
				
				if (handler != null){
					handler.onMethodExtracted(sc, method);
				}
				
				Type retType = method.getReturnType();
				
				//A list is a ref type as well
				if (retType instanceof RefType){
					RefType refType = (RefType) retType;
					String retClassName = refType.getClassName();
					
					
					//TODO fill this in
					if (retClassName.equals("java.util.List")){
						
					} else if (retClassName.equals("java.lang.Object")){
					
					} else {
						
						
						if (!classes.contains(retClassName) && !retClassName.startsWith("java.")){
							

							
							
							getMethodReturnClassNames(Scene.v().getSootClass(retClassName), classes);
						}
					}
				}	
			}
		}
	}
	
	/**
	 * Excludes Object
	 * @return
	 */
	private List<String> getSuperClassNames(SootClass sootClass ){
		List<String> superClassNames = new ArrayList<String>();
		List<SootClass> superClasses = Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(sootClass);
		Iterator<SootClass> it = superClasses.iterator();
		while (it.hasNext()){
			SootClass sc = it.next();
			if (!sc.getName().equals("java.lang.Object")){
				superClassNames.add(sc.getName());
			}
		}
		return superClassNames;
	}


	/**
	 * Excludes Object, derived from unmodifiable list so create a new one without Object
	 * @return
	 */
	private List<SootClass> getSuperSootClasses(SootClass sootClass ){
		List<SootClass> superClasses = Scene.v().getActiveHierarchy().getSuperclassesOfIncluding(sootClass);

		List<SootClass> superClassesWithoutObject = new ArrayList<SootClass>();
		for (int i=0; i< superClasses.size(); i++){
			SootClass sc = superClasses.get(i);
			if (!sc.getName().equals("java.lang.Object")){
				superClassesWithoutObject.add(sc);
			}
		}
		
		return superClassesWithoutObject;
	}

}
