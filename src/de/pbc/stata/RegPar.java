package de.pbc.stata;

import java.util.Collection;
import java.util.Collections;

import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.SFIToolkit;

public abstract class RegPar {
	
	// VARIBALES ---------------------------------------------------- //
	
	private Variable dv;
	
	private String[] termNames, termEquations;
	
	private double[][] resultsTable;
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	protected RegPar() {
		this.dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
		this.termNames = Matrix.getMatrixColNames("r(table)");
		this.resultsTable = StataUtils.getMatrix("r(table)");
		
		if (hasMultipleEquations()) {
			SFIToolkit.executeCommand("local coleq : coleq e(b)", false);
			termEquations = StataUtils.getMacroArray("coleq");
		}
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public abstract Collection<ModelStat> getStats();
	
	public Variable getDv() {
		return dv;
	}
	
	public String[] getTermNames() {
		return termNames;
	}
	
	public String[] getTermEquations() {
		return termEquations;
	}
	
	public double[][] getResultsTable() {
		return resultsTable;
	}
	
	public void setDv(Variable dv) {
		this.dv = dv;
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
	
	public Collection<ModelStat> getEquationStats(String eq) {
		return Collections.emptyList();
	}
	
}