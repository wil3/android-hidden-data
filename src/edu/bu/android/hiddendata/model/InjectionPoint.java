package edu.bu.android.hiddendata.model;

public class InjectionPoint {
	private String declaredClass;
	private String methodSignature;
	private String targetInstruction;
	private String classToInject;
	
	public String getDeclaredClass() {
		return declaredClass;
	}
	public void setDeclaredClass(String declaredClass) {
		this.declaredClass = declaredClass;
	}
	public String getMethodSignature() {
		return methodSignature;
	}
	public void setMethodSignature(String methodSignature) {
		this.methodSignature = methodSignature;
	}
	public String getTargetInstruction() {
		return targetInstruction;
	}
	public void setTargetInstruction(String targetInstruction) {
		this.targetInstruction = targetInstruction;
	}
	public String getClassToInject() {
		return classToInject;
	}
	public void setClassToInject(String classToInject) {
		this.classToInject = classToInject;
	}
	
	
}
