package de.pbc.stata;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.apache.commons.math3.distribution.TDistribution;

import com.stata.sfi.Data;
import com.stata.sfi.Macro;
import com.stata.sfi.Matrix;
import com.stata.sfi.Scalar;

public class Models {
	
	// PUBLIC ------------------------------------------------------- //
	
	public static ModelResult byCmd(String cmd) {
		switch (cmd) {
			case "regress":
				return new StandardResult() {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("F", null, "F"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "ivregress":
				return new StandardResult() {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("chi2", null, "Wald χ²"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "xtivreg":
			case "xtreg":
				return new StandardResult() {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("F", "p", "F"),
								new ModelStat("r2", null, "R²"),
								new ModelStat("r2_a", null, "R² (adj.)"),
								new ModelStat("N", null, "N", 0),
								new ModelStat("N_g", null, "Groups", 0));
					}
					
				};
			case "logit":
				return new StandardResult() {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("chi2", "p", "Wald χ²"),
								new ModelStat("r2_p", null, "Pseudo R²"),
								new ModelStat("N", null, "N", 0));
					}
					
				};
			case "sqreg":
				return new StandardResult(true) {
					
					protected Map<String, List<ModelStat>> eqStats;
					
					protected List<ModelStat> modelStats;
					
					@Override
					public List<ModelStat> getModelStats() {
						if (Objects.isNull(modelStats)) {
							modelStats = List.of(new ModelStat("N", null, "N", 0));
						}
						return modelStats;
					}
					
					@Override
					public List<ModelStat> getEquationStats(String eq) {
						if (Objects.isNull(eqStats)) {
							eqStats = new HashMap<>(getEquations().size());
							for (int i = 1; i <= getEquations().size(); i++) {
								double sumrdv = Scalar.getValue(String.format("sumrdv%d", i), Scalar.TYPE_ERETURN);
								double sumadv = Scalar.getValue(String.format("sumadv%d", i), Scalar.TYPE_ERETURN);
								double r2_p = 1 - (sumadv / sumrdv);
								eqStats.computeIfAbsent(getEquations().get(i - 1), k -> new ArrayList<>())
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
			case "qrprocess":
				return new Qrprocess();
			case "heckman":
				return new StandardResult(true) {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("chi2", "p", "Wald χ²"), new ModelStat("lambda", null, "λ") {
							
							private Double se;
							
							@Override
							protected void getValues() {
								// the last value in the regression is lambda
								val = resultsTable[0][termNames.size() - 1];
								val = Data.isValueMissing(val) ? null : val;
								
								se = resultsTable[1][termNames.size() - 1];
								se = Data.isValueMissing(se) ? null : se;
								
								p = resultsTable[3][termNames.size() - 1];
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
					public List<String> getEquations() {
						return List.of("select", getDv().getLabel());
					}
					
				};
			default:
				return new StandardResult() {
					
					@Override
					public List<ModelStat> getModelStats() {
						return List.of(new ModelStat("N", null, "N", 0));
					}
					
				};
		}
	}
	
	// INNER CLASSES ------------------------------------------------ //
	
	protected static class Qrprocess implements ModelResult {
		
		// VARIABLES ------------------------------------------------ //
		
		protected Variable dv;
		
		protected List<String> termNames, equations, equationLabels;
		
		protected double[][] coefs, V;
		
		protected TDistribution t;
		
		protected Map<String, List<Term>> termsMap = new HashMap<>();
		
		protected List<ModelStat> modelStats;
		
		protected Map<String, List<ModelStat>> equationStats;
		
		// CONSTRUCTOR ---------------------------------------------- //
		
		public Qrprocess() {
			this.dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
			this.termNames = Arrays.asList(Matrix.getMatrixRowNames("e(coefmat)"));
			this.equations = Arrays.asList(Matrix.getMatrixColNames("e(coefmat)"));
			this.equationLabels = Arrays.stream(StataUtils.getMatrix("e(quantiles)"))
					.<String>map(row -> String.format("Q=%s", row[0]))
					.collect(Collectors.toList());
			this.coefs = StataUtils.getMatrix("e(coefmat)");
			this.V = StataUtils.getMatrix("e(V)");
			this.t = new TDistribution(Scalar.getValue("df_r", Scalar.TYPE_ERETURN));
			
			for (int i = 0; i < equations.size(); i++) {
				String eqLabel = equationLabels.get(i);
				List<Term> terms = new ArrayList<>();
				for (int j = 0; j < termNames.size(); j++) {
					String termName = termNames.get(j);
					
					if (coefs[j][i] == 0 && V[j + termNames.size() * i][j + termNames.size() * i] == 0) {
						// could not be estimated
						terms.add(new Term(j, termName, null, null, null));
					} else {
						terms.add(new Term(j,
								termName,
								coefs[j][i],
								Math.sqrt(V[j + termNames.size() * i][j + termNames.size() * i]),
								// there's no upper tail t distribution in Java. Instead, subtract from 1.
								2 * (1 - t.cumulativeProbability(Math.abs(coefs[j][i] / Math.sqrt(V[j + termNames.size()
										* i][j + termNames.size() * i]))))));
					}
				}
				termsMap.put(eqLabel, terms);
			}
			
			modelStats = List.of(new ModelStat("N", null, "N", 0));
			
			equationStats = new HashMap<>(equations.size());
			double[][] sumadv = StataUtils.getMatrix("e(sum_mdev)");
			double[][] sumrdv = StataUtils.getMatrix("e(sum_rdev)");
			for (int i = 0; i < equations.size(); i++) {
				double r2_p = 1 - (sumadv[0][i] / sumrdv[0][i]);
				equationStats.computeIfAbsent(equationLabels.get(i), k -> new ArrayList<>())
						.add(new ModelStat("r2_p", null, "Pseudo R²") {
							
							protected void getValues() {
								val = r2_p;
							};
							
						});
			}
		}
		
		// PUBLIC --------------------------------------------------- //
		
		@Override
		public Variable getDv() {
			return dv;
		}
		
		public boolean hasMultipleEquations() {
			return true;
		}
		
		public List<String> getEquations() {
			return equationLabels;
		}
		
		@Override
		public List<Term> getTerms() {
			return termsMap.get(equationLabels.get(0));
		}
		
		@Override
		public List<Term> getTerms(String eq) {
			return termsMap.get(eq);
		}
		
		@Override
		public List<ModelStat> getModelStats() {
			return modelStats;
		}
		
		public List<ModelStat> getEquationStats(String eq) {
			return equationStats.get(eq);
		}
		
	}
	
}