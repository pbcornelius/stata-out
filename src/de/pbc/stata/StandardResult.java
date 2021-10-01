package de.pbc.stata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.SFIToolkit;

/*
 * Standard Stata model.
 */
public abstract class StandardResult implements ModelResult {
	
	// CONSTANTS ---------------------------------------------------- //
	
	private static String EQUATION0 = "0";
	
	// VARIABLES ---------------------------------------------------- //
	
	protected boolean hasMultipleEquations;
	
	protected Variable dv;
	
	protected List<String> termNames, termEquations, equations;
	
	protected double[][] resultsTable;
	
	protected Map<String, List<Term>> termsMap = new HashMap<>();
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	protected StandardResult() {
		this(false);
	}
	
	protected StandardResult(boolean hasMultipleEquations) {
		this.hasMultipleEquations = hasMultipleEquations;
		init();
	}
	
	// PROTECTED ---------------------------------------------------- //
	
	protected void init() {
		this.dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
		this.termNames = Arrays.asList(Matrix.getMatrixColNames("r(table)"));
		this.resultsTable = StataUtils.getMatrix("r(table)");
		
		if (hasMultipleEquations()) {
			SFIToolkit.executeCommand("local coleq : coleq e(b)", false);
			termEquations = Arrays.asList(StataUtils.getMacroArray("coleq"));
			equations = termEquations.stream().distinct().collect(Collectors.toList());
			
			for (String eq : getEquations()) {
				List<Term> terms = new ArrayList<>();
				for (int col = 0; col < termEquations.size(); col++) {
					if (termEquations.get(col).equals(eq)) {
						terms.add(new Term(col,
								termNames.get(col),
								resultsTable[0][col],
								resultsTable[1][col],
								resultsTable[3][col]));
					}
				}
				termsMap.put(eq, terms);
			}
		} else {
			List<Term> terms = new ArrayList<>(termNames.size());
			for (int i = 0; i < termNames.size(); i++)
				terms.add(new Term(i, termNames.get(i), resultsTable[0][i], resultsTable[1][i], resultsTable[3][i]));
			termsMap.put(EQUATION0, terms);
		}
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	@Override
	public Variable getDv() {
		return dv;
	}
	
	@Override
	public boolean hasMultipleEquations() {
		return hasMultipleEquations;
	}
	
	@Override
	public List<String> getEquations() {
		return equations;
	}
	
	@Override
	public List<Term> getTerms() {
		return termsMap.get(EQUATION0);
	}
	
	@Override
	public List<Term> getTerms(String eq) {
		return termsMap.get(eq);
	}
	
}