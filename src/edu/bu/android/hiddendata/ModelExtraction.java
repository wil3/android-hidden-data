package edu.bu.android.hiddendata;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.bu.android.hiddendata.parser.SignatureParser;
import soot.RefType;
import soot.Scene;
import soot.SootClass;
import soot.SootMethod;
import soot.Type;
import soot.VoidType;
import soot.tagkit.SignatureTag;
import soot.tagkit.Tag;
import sun.reflect.generics.tree.*;

/**
 * A class for handling all sorts of model (class) extractions
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
	private static final String PATTERN_GENERIC_CLASS = "\\(\\)[\\w/]+<\\w([\\w/]+);>;";
	 Pattern pattern = Pattern.compile(PATTERN_GENERIC_CLASS);

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
	public Set<String> getModels(String modelClassName){
		
		SootClass sootClass = Scene.v().getSootClass(modelClassName);
			
		//need to look at all the super methods too because although the child class can access them all
		//they are not represented in Java this way
		
		Set<String> classes = new HashSet<String>();
		getMethodReturnClassNames(sootClass, classes, null);
		
		for (String c : classes){
			logger.debug("Class {}", c);
		}
		return classes;
	}
	
	private void getMethodReturnClassNames(SootClass sootClass, Set<String> classes, ClassTypeSignature superTypeSignature){
		ClassTypeSignature childSuperTypeSignature = null;
		ClassTypeSignature currentSuperTypeSignature = null;

		//For all the classes
		for (SootClass sc : getSuperSootClasses(sootClass)){
			classes.add(sc.getName());
			
			if (sc.getName().contains("AnnotationTest")){
				logger.debug("");
			}
			//Look at the class signature, see if it has any type parameters that need to be replaced 
			//in the methods
			String signatureTag = FlowAnalyzer.getClassSignatureFromTag(sc.getTags());
			if (signatureTag != null){
				SignatureParser sp = SignatureParser.make();
				ClassSignature classSignature = sp.parseClassSig(signatureTag);
				FormalTypeParameter [] formalTypeParameters = classSignature.getFormalTypeParameters();
				for (FormalTypeParameter ftp : formalTypeParameters){
				}
				currentSuperTypeSignature = classSignature.getSuperclass();
				logger.debug("");

			}
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
					
					
					String methodSignatureTag = FlowAnalyzer.getClassSignatureFromTag( method.getTags());
					if (methodSignatureTag != null){
						SignatureParser sp = SignatureParser.make();
						MethodTypeSignature methodTypeSignature = sp.parseMethodSig(methodSignatureTag);
						FormalTypeParameter [] formalTypeParameters = methodTypeSignature.getFormalTypeParameters();
						ReturnType returnType = methodTypeSignature.getReturnType();
						if (returnType instanceof TypeVariableSignature){
							String identifier = ((TypeVariableSignature) returnType).getIdentifier();
							if (childSuperTypeSignature != null){
							}
						}
						logger.trace("");
					}
					//Check if there is a generic
					/*
					for (Tag tag : method.getTags()){
						if (tag instanceof SignatureTag){
							String name = ((SignatureTag) tag).getSignature();
							String byteCodeClassName = parseClassNameFromAnnotation(name);
							if (byteCodeClassName == null) { continue;}
							String className = FlowAnalyzer.convertBytecodeToJavaClassName(byteCodeClassName);
							getMethodReturnClassNames(Scene.v().getSootClass(className), classes);
							continue;
						}
					}
					*/
					
					//TODO fill this in
					if (retClassName.equals("java.util.List")){
						
					} else if (retClassName.equals("java.lang.Object")){
					
					} else {
						
						
						if (!classes.contains(retClassName) && !retClassName.startsWith("java.")){
							if (currentSuperTypeSignature != null){
								getMethodReturnClassNames(Scene.v().getSootClass(retClassName), classes, currentSuperTypeSignature);
							} else {
								getMethodReturnClassNames(Scene.v().getSootClass(retClassName), classes, superTypeSignature);
							}
						}
					}
				}	
			}
			childSuperTypeSignature = currentSuperTypeSignature;
		}
	}
	
	/**
	 * 
	 * @param path The super class path. Path because of nesting classes.
	 * @return
	 */
	@SuppressWarnings("restriction")
	private List<String> getTypeByIdentifier(List<SimpleClassTypeSignature> path){
		List<String> classes = new ArrayList<String>();
		if (path == null){
			return classes;
		}
		
		//Seem to be in heiarchy order from parent up
		//TODO if super super how does this work?
		for (SimpleClassTypeSignature superClass : path){
			
			//Look at the type arguments for each super class
			for (TypeArgument typeArg : superClass.getTypeArguments()){
				if (typeArg instanceof ClassTypeSignature){
					List<String> foundTypeClasses = new ArrayList<String>();
					collectTypeParameterClassNames((ClassTypeSignature)typeArg, foundTypeClasses);
					classes.addAll(foundTypeClasses);
				}
			}
		}
		return classes;
	}
	
	/**
	 * Recursively handle the path
	 * @param cts
	 * @param path
	 */
	@SuppressWarnings("restriction")
	private void collectTypeParameterClassNames(ClassTypeSignature cts,  List<String> path){
		for (SimpleClassTypeSignature p : cts.getPath()){
			if (!FlowAnalyzer.isAndroidFramework(p.getName())){
				path.add(p.getName());
			}
			//Look at the type arguments for each super class
			for (TypeArgument typeArg : p.getTypeArguments()){
				//typeArg.
				if (typeArg instanceof ClassTypeSignature){
					//((ClassTypeSignature)typeArg).getPath()
					collectTypeParameterClassNames((ClassTypeSignature) typeArg, path);
				}
			}
		}
	}
	
	private String parseClassNameFromAnnotation(String signatureTag){
	        Matcher matcher = pattern.matcher(signatureTag);
	        if (matcher.matches()){
	        	return matcher.group(1);
	        }
	        return null;
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
		
		String signatureTag = FlowAnalyzer.getClassSignatureFromTag(sootClass.getTags());
		if (signatureTag != null){
			SignatureParser sp = SignatureParser.make();
			ClassSignature classSignature = sp.parseClassSig(signatureTag);
			FormalTypeParameter [] formalTypeParameters = classSignature.getFormalTypeParameters();
			for (FormalTypeParameter ftp : formalTypeParameters){
			}
			ClassTypeSignature currentSuperTypeSignature = classSignature.getSuperclass();
			logger.debug("");
			
			for (String _sc : getTypeByIdentifier(currentSuperTypeSignature.getPath()) ){
				superClassesWithoutObject.add(Scene.v().getSootClass(_sc));
			}


		}
		
		return superClassesWithoutObject;
	}
	
	@SuppressWarnings("restriction")
	public List<String> getTypeParameters(String sootSignatureTag){
		SignatureParser sp = SignatureParser.make();
		ClassSignature classSignature = sp.parseClassSig(sootSignatureTag);
		FormalTypeParameter [] formalTypeParameters = classSignature.getFormalTypeParameters();
		ClassTypeSignature currentSuperTypeSignature = classSignature.getSuperclass();
		return getTypeByIdentifier(currentSuperTypeSignature.getPath());
	}
	
	@SuppressWarnings("restriction")
	public boolean isReturnATypeParameter(String sootSignatureTag){
		if (sootSignatureTag != null){
			SignatureParser sp = SignatureParser.make();
			MethodTypeSignature methodTypeSignature = sp.parseMethodSig(sootSignatureTag);
			ReturnType returnType = methodTypeSignature.getReturnType();
			if (returnType instanceof TypeVariableSignature){
				String identifier = ((TypeVariableSignature) returnType).getIdentifier();
				if (identifier != null){
					return true;
				}

			}
		}
		return false;
	}

}
