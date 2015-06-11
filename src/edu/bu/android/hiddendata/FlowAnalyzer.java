package edu.bu.android.hiddendata;

import java.util.Iterator;

import soot.jimple.infoflow.android.SetupApplication;
import soot.jimple.infoflow.android.source.data.SourceSinkDefinition;

public abstract class FlowAnalyzer {
	protected SetupApplication context;

	public FlowAnalyzer(SetupApplication context) {
		this.context = context;

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
}
