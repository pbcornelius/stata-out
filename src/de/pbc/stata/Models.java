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
import com.stata.sfi.SFIToolkit;
import com.stata.sfi.Scalar;

public class Models {

	// PUBLIC ------------------------------------------------------- //

	public static ModelResult byCmd(String cmd) {
		switch (cmd) {
		case "regress":
			return new StandardResult() {

				@Override
				public List<ModelStat> getModelStats() {
					return List.of(new ModelStat.FStat("F", "F"), new ModelStat("r2", null, "R²"),
							new ModelStat("r2_a", null, "R² (adj.)"), new ModelStat("N", null, "N", 0));
				}

			};
		case "ivregress":
			return new StandardResult() {

				@Override
				public List<ModelStat> getModelStats() {
					return List.of(new ModelStat("chi2", null, "Wald χ²"), new ModelStat("r2", null, "R²"),
							new ModelStat("r2_a", null, "R² (adj.)"), new ModelStat("N", null, "N", 0));
				}

			};
		case "xtivreg":
		case "xtreg":
			return new StandardResult() {

				@Override
				public List<ModelStat> getModelStats() {
					return List.of(new ModelStat("F", "p", "F"), new ModelStat("r2", null, "R²"),
							new ModelStat("r2_a", null, "R² (adj.)"), new ModelStat("N", null, "N", 0),
							new ModelStat("N_g", null, "Groups", 0));
				}

			};
		case "reghdfe":
			return new StandardResult() {

				@Override
				public List<ModelStat> getModelStats() {
					return List.of(new ModelStat.FStat("F", "F"), new ModelStat("r2", null, "R²"),
							new ModelStat("r2_a", null, "R² (adj.)"), new ModelStat("N", null, "N", 0));
				}

			};
		case "ivreghdfe":
			return new Ivreghdfe();
		case "logit":
			return new StandardResult() {

				@Override
				public List<ModelStat> getModelStats() {
					return List.of(new ModelStat("chi2", "p", "Wald χ²"), new ModelStat("r2_p", null, "Pseudo R²"),
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

				private Map<String, List<ModelStat>> equationStats;

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
							return getVal() + getSigStars()
									+ (Objects.nonNull(se) ? (" (" + StataUtils.correctRounding(se, defScale) + ")")
											: "");
						}

					}, new ModelStat("rho", "p_c", "ρ"));
				}

				@Override
				public List<String> getEquations() {
					return List.of("select", getDv().getLabel());
				}

				public List<ModelStat> getEquationStats(String eq) {
					if (Objects.isNull(equationStats)) {
						equationStats = new HashMap<>(getEquations().size());
						equationStats.put("select", List.of(new ModelStat("N", null, "N", 0)));
						equationStats.put(getDv().getLabel(), List.of(new ModelStat("N_selected", null, "N", 0)));
					}
					return equationStats.get(eq);
				};

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
					.<String>map(row -> String.format("Q=%s", row[0])).collect(Collectors.toList());
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
						terms.add(new Term(j, termName, coefs[j][i],
								Math.sqrt(V[j + termNames.size() * i][j + termNames.size() * i]),
								// there's no upper tail t distribution in Java. Instead, subtract from 1.
								2 * (1 - t.cumulativeProbability(Math.abs(coefs[j][i]
										/ Math.sqrt(V[j + termNames.size() * i][j + termNames.size() * i]))))));
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

	protected static class Ivreghdfe implements ModelResult {

		// VARIABLES ------------------------------------------------ //

		protected Variable dv;

		protected List<String> eqs;

		protected Map<String, List<Term>> eqTerms = new HashMap<>();

		protected List<ModelStat> modelStats = new ArrayList<>();

		protected Map<String, List<ModelStat>> eqStats = new HashMap<>();

		// CONSTRUCTOR ---------------------------------------------- //

		protected Ivreghdfe() {
			this.dv = new Variable(Macro.getGlobal("depvar", Macro.TYPE_ERETURN));
			this.eqs = new ArrayList<>(Arrays.asList(Macro.getGlobal("instd", Macro.TYPE_ERETURN).split(" ")));
			this.eqs.add(getDv().getName());

			// 2nd stage

			stage2();

			for (String eq : eqs.stream().filter(eq -> !eq.equals(getDv().getName())).toList()) {
				stage1Stats(eq);
			}

			for (String eq : eqs.stream().filter(eq -> !eq.equals(getDv().getName())).toList()) {
				stage1Coefs(eq);
			}
		}

		// PRIVATE -------------------------------------------------- //

		private void stage2() {
			// coefs

			double[] b = StataUtils.getMatrix("e(b)")[0];
			double[][] V = StataUtils.getMatrix("e(V)");
			String[] cols = Matrix.getMatrixColNames("e(b)");
			TDistribution t = new TDistribution(Scalar.getValue("df_r", Scalar.TYPE_ERETURN));
			List<Term> terms = new ArrayList<>(cols.length);
			for (int col = 0; col < cols.length; col++) {
				terms.add(new Term(col, cols[col], b[col], Math.sqrt(V[col][col]),
						// there's no upper tail t distribution in Java; instead, subtract from 1.
						2 * (1 - t.cumulativeProbability(Math.abs(b[col] / Math.sqrt(V[col][col]))))));
			}
			eqTerms.put(getDv().getName(), terms);

			// model stats

			modelStats.add(new ModelStat("N", null, "N", 0));

			// equation stats

			eqStats.put(getDv().getName(), List.of(new ModelStat("F", "Fp", "F"), new ModelStat("r2", null, "R²"),
					new ModelStat("r2_a", null, "R² (adj.)")));
		}

		private void stage1Stats(String eq) {
			double[] eqStat = StataUtils.transposeMatrix(StataUtils.getMatrix("e(first)"))[Arrays
					.asList(Matrix.getMatrixColNames("e(first)")).indexOf(eq)];
			List<String> eqStatRows = Arrays.asList(Matrix.getMatrixRowNames("e(first)"));

			eqStats.put(eq,
					List.of(new ModelStat("F", "F", eqStat[eqStatRows.indexOf("F")],
							eqStat[eqStatRows.indexOf("pvalue")]),
							new ModelStat("pr2", "R²", eqStat[eqStatRows.indexOf("pr2")], null)));
		}

		private void stage1Coefs(String eq) {
			// this call removes e(first), so stage 1 stats have to be collected beforehand
			SFIToolkit.executeCommand(String.format("quietly: estimates restore _ivreg2_%s", eq), false);

			double[] b = StataUtils.getMatrix("e(b)")[0];
			double[][] V = StataUtils.getMatrix("e(V)");
			String[] cols = Matrix.getMatrixColNames("e(b)");
			TDistribution t = new TDistribution(Scalar.getValue("df_r", Scalar.TYPE_ERETURN));
			List<Term> terms = new ArrayList<>(cols.length);
			for (int col = 0; col < cols.length; col++) {
				terms.add(new Term(col, cols[col], b[col], Math.sqrt(V[col][col]),
						// there's no upper tail t distribution in Java; instead, subtract from 1.
						2 * (1 - t.cumulativeProbability(Math.abs(b[col] / Math.sqrt(V[col][col]))))));
			}
			eqTerms.put(eq, terms);
		}

		// PUBLIC --------------------------------------------------- //

		@Override
		public Variable getDv() {
			return dv;
		}

		@Override
		public boolean hasMultipleEquations() {
			return true;
		}

		@Override
		public List<String> getEquations() {
			return eqs;
		}

		@Override
		public List<Term> getTerms() {
			return eqTerms.get(getDv().getName());
		}

		@Override
		public List<Term> getTerms(String eq) {
			return eqTerms.get(eq);
		}

		@Override
		public List<ModelStat> getModelStats() {
			return modelStats;
		}

		@Override
		public List<ModelStat> getEquationStats(String eq) {
			return eqStats.get(eq);
		}
	}

}