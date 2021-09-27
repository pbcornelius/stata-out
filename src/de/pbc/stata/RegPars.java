package de.pbc.stata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import com.stata.sfi.Data;
import com.stata.sfi.Scalar;

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
					
					protected Map<String, Collection<ModelStat>> eqStats;
					
					protected Collection<ModelStat> modelStats;
					
					@Override
					public Collection<ModelStat> getStats() {
						if (Objects.isNull(modelStats)) {
							modelStats = List.of(new ModelStat("N", null, "N", 0));
						}
						return modelStats;
					}
					
					@Override
					public boolean hasMultipleEquations() {
						return true;
					}
					
					@Override
					public Collection<ModelStat> getEquationStats(String eq) {
						if (Objects.isNull(eqStats)) {
							List<String> eqs = Arrays.stream(getTermEquations())
									.distinct()
									.collect(Collectors.toList());
							eqStats = new HashMap<>(eqs.size());
							for (int i = 1; i <= eqs.size(); i++) {
								double sumrdv = Scalar.getValue(String.format("sumrdv%d", i), Scalar.TYPE_ERETURN);
								double sumadv = Scalar.getValue(String.format("sumadv%d", i), Scalar.TYPE_ERETURN);
								double r2_p = 1 - (sumadv / sumrdv);
								eqStats.computeIfAbsent(eqs.get(i - 1), k -> new ArrayList<>())
										.add(new ModelStat("r2_p", null, "Pseudo R²") {
											
											protected void getValues() {
												val = r2_p;
											};
											
										});
							}
						}
						
						return eqStats.get(eq);
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