package edu.bu.android.hiddendata.model;

import java.util.Collection;
import java.util.Map;

public class Results {
	private String apkName;
	private int callGraphEdges;
	//private boolean hasObfuscation;
	private Collection<String> usedGetMethodSignatures;
	private Collection<String> hiddenGetMethodSignatures;
	private Map<String, Integer> getMethodsInApp;
	
	public String getApkName() {
		return apkName;
	}
	public void setApkName(String apkName) {
		this.apkName = apkName;
	}
	/*
	public boolean isHasObfuscation() {
		return hasObfuscation;
	}
	public void setHasObfuscation(boolean hasObfuscation) {
		this.hasObfuscation = hasObfuscation;
	}
	*/
	public Collection<String> getUsedGetMethodSignatures() {
		return usedGetMethodSignatures;
	}
	public void setUsedGetMethodSignatures(Collection<String> getMethodSignatures) {
		this.usedGetMethodSignatures = getMethodSignatures;
	}
	public Collection<String> getHiddenGetMethodSignatures() {
		return hiddenGetMethodSignatures;
	}
	public void setHiddenGetMethodSignatures(
			Collection<String> hiddenGetMethodSignatures) {
		this.hiddenGetMethodSignatures = hiddenGetMethodSignatures;
	}
	public int getCallGraphEdges() {
		return callGraphEdges;
	}
	public void setCallGraphEdges(int callGraphEdges) {
		this.callGraphEdges = callGraphEdges;
	}
	public Map<String, Integer> getGetMethodsInApp() {
		return getMethodsInApp;
	}
	public void setGetMethodsInApp(Map<String, Integer> getMethodsInApp) {
		this.getMethodsInApp = getMethodsInApp;
	}

}
