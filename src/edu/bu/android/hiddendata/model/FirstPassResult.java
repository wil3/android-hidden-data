package edu.bu.android.hiddendata.model;

import java.util.List;

public class FirstPassResult {

	private List<String> getMethodSignatures;
	private List<String> modelNames;
	private List<InjectionPoint> injections;

	private List<Model> models;

	public List<Model> getModels() {
		return models;
	}

	public void setModels(List<Model> models) {
		this.models = models;
	}
	
	public List<String> getGetMethodSignatures() {
		return getMethodSignatures;
	}
	public void setGetMethodSignatures(List<String> getMethodSignatures) {
		this.getMethodSignatures = getMethodSignatures;
	}

	public List<String> getModelNames() {
		return modelNames;
	}

	public void setModelNames(List<String> modelNames) {
		this.modelNames = modelNames;
	}

	public List<InjectionPoint> getInjections() {
		return injections;
	}

	public void setInjections(List<InjectionPoint> injections) {
		this.injections = injections;
	}

}
