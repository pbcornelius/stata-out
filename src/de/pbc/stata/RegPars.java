package de.pbc.stata;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import com.stata.sfi.Data;

public class RegPars {
	
	// PUBLIC ------------------------------------------------------- //
	
	public static RegPar byCmd(String cmd) {
		switch (cmd) {
			case "regress":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("F", null, "F"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "ivregress":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("chi2", null, "Wald χ²"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "xtivreg":
			case "xtreg":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("F", "p", "F"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0),
								new ModelStat("N_g", null, "Groups", 0));
					}
					
				};
			case "logit":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("chi2", "p", "Wald χ²"),
								new ModelStat("r2_p", null, "Pseudo R²"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "sqreg":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("N", null, "N", 0));
					}
					
					@Override
					public boolean hasMultipleEquations() {
						return true;
					}
					
				};
			case "heckman":
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("chi2", "p", "Wald χ²"), new ModelStat("lambda", null, "λ") {
							
							private Double se;
							
							@Override
							protected void getValues() {
								// the last value in the regression is lambda
								val = getResultsTable()[0][getTermNames().length - 1];
								val = Data.isValueMissing(val) ? null : val;
								
								se = getResultsTable()[1][getTermNames().length - 1];
								se = Data.isValueMissing(se) ? null : se;
								
								p = getResultsTable()[3][getTermNames().length - 1];
								p = Data.isValueMissing(p) ? null : p;
							}
							
							@Override
							public String toString() {
								return getVal() + getSigStars() + (Objects.nonNull(se)
										? (" (" + StataUtils.correctRounding(se, defScale) + ")")
										: "");
							}
							
						}, new ModelStat("rho", "p_c", "ρ"), new ModelStat("N", null, "N", 0));
					}
					
					@Override
					public boolean hasMultipleEquations() {
						return true;
					}
					
					@Override
					public boolean hasDefinedMultipleEquations() {
						return true;
					}
					
					@Override
					public String[] getDefinedMultipleEquations() {
						return new String[] { "select", getDv().getLabel() };
					}
					
				};
			default:
				return new RegPar() {
					
					@Override
					public Collection<ModelStat> getStats() {
						return List.of(new ModelStat("N", null, "N", 0));
					}
					
				};
		}
	}
	
}