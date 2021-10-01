package de.pbc.stata;

import java.util.List;

public interface ModelResult {
	
	// PUBLIC ------------------------------------------------------- //
	
	public Variable getDv();
	
	public default boolean hasMultipleEquations() {
		return false;
	}
	
	public default List<String> getEquations() {
		return null;
	}
	
	public List<Term> getTerms();
	
	public default List<Term> getTerms(String eq) {
		return getTerms();
	}
	
	public List<ModelStat> getModelStats();
	
	public default List<ModelStat> getEquationStats(String eq) {
		return null;
	}
	
}