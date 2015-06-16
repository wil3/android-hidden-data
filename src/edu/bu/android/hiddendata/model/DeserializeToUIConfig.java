package edu.bu.android.hiddendata.model;

import java.util.Collection;
import java.util.List;

public class DeserializeToUIConfig {

	private Collection<String> getMethodSignatures;
	private Collection<String> modelNames;
	private Collection<InjectionPoint> injections;

	private Collection<Model> models;

	public Collection<Model> getModels() {
		return models;
	}

	public void setModels(Collection<Model> models) {
		this.models = models;
	}
	
	public Collection<String> getGetMethodSignatures() {
		return getMethodSignatures;
	}
	public void setGetMethodSignatures(Collection<String> getMethodSignatures) {
		this.getMethodSignatures = getMethodSignatures;
	}

	public Collection<String> getModelNames() {
		return modelNames;
	}

	public void setModelNames(Collection<String> modelNames) {
		this.modelNames = modelNames;
	}

	public Collection<InjectionPoint> getInjections() {
		return injections;
	}

	public void setInjections(Collection<InjectionPoint> injections) {
		this.injections = injections;
	}

}
