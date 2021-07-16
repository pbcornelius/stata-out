package de.pbc.stata;

import java.util.Collection;
import java.util.List;

public class RegPars {
	
	// PUBLIC ------------------------------------------------------- //
	
	public static RegPar byCmd(String cmd, Variable dv) {
		switch (cmd) {
			case "regress":
				return ols(dv);
			case "logit":
				return logit(dv);
			case "ivregress":
				return twoSls(dv);
			case "xtreg":
				return olsFe(dv);
			case "xtivreg":
				return olsFe(dv);
			case "sqreg":
				return sqreg(dv);
			case "heckman":
				return heckman(dv);
			default:
				return ols(dv);
		}
	}
	
	public static RegPar ols(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("F", null, "F"),
						new ModelStat("r2", null, "R²"),
						new ModelStat("r2_a", null, "R² (adj.)"),
						new ModelStat("N", null, "N", 0));
			}
			
		};
	}
	
	public static RegPar olsFe(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("F", "p", "F"),
						new ModelStat("r2", null, "R²"),
						new ModelStat("r2_a", null, "R² (adj.)"),
						new ModelStat("N", null, "N", 0),
						new ModelStat("N_g", null, "Groups", 0));
			}
			
		};
	}
	
	public static RegPar twoSls(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("chi2", null, "Wald χ²"),
						new ModelStat("r2", null, "R²"),
						new ModelStat("r2_a", null, "R² (adj.)"),
						new ModelStat("N", null, "N", 0));
			}
			
		};
	}
	
	public static RegPar logit(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("chi2", "p", "Wald χ²"),
						new ModelStat("r2_p", null, "Pseudo R²"),
						new ModelStat("N", null, "N", 0));
			}
			
		};
	}
	
	public static RegPar sqreg(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("N", null, "N", 0));
			}
			
			@Override
			public boolean hasMultipleEquations() {
				return true;
			}
			
		};
	}
	
	public static RegPar heckman(Variable dv) {
		return new RegPar(dv) {
			
			@Override
			public Collection<ModelStat> getStats() {
				return List.of(new ModelStat("chi2", "p", "Wald χ²"),
						new ModelStat("rho", "p_c", "ρ"),
						new ModelStat("N", null, "N", 0));
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
	}
	
}