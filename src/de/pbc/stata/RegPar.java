package de.pbc.stata;

import java.util.Collection;

public abstract class RegPar {
	
	// VARIBALES ---------------------------------------------------- //
	
	protected Variable dv;
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	protected RegPar(Variable dv) {
		this.dv = dv;
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public abstract Collection<ModelStat> getStats();
	
	public Variable getDv() {
		return dv;
	}
	
	public boolean hasMultipleEquations() {
		return false;
	}
	
	public boolean hasDefinedMultipleEquations() {
		return false;
	}
	
	public String[] getDefinedMultipleEquations() {
		return null;
	}
	
}