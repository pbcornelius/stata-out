package de.pbc.stata;

public class RegPars {
	
	// PUBLIC ------------------------------------------------------- //
	
	public static RegPar byCmd(String cmd) {
		switch (cmd) {
			case "regress":
				return ols();
			case "logit":
				return logit();
			case "ivregress":
				return twoSls();
			case "xtreg":
				return olsFe();
			case "xtivreg":
				return olsFe();
			default:
				return ols();
		}
	}
	
	public static RegPar ols() {
		return new RegPar() {
			
			@Override
			public String getStatName() {
				return "F-Stat";
			}
			
			@Override
			public String getStatId() {
				return "F";
			}
		};
	}
	
	public static RegPar olsFe() {
		return new RegPar() {
			
			@Override
			public boolean hasGroups() {
				return true;
			}
			
			@Override
			public String getStatName() {
				return "F-Stat";
			}
			
			@Override
			public String getStatId() {
				return "F";
			}
		};
	}
	
	public static RegPar twoSls() {
		return new RegPar() {
			
			@Override
			public boolean isTwoStages() {
				return true;
			}
			
			@Override
			public String getStatName() {
				return "Wald chi2";
			}
			
			@Override
			public String getStatId() {
				return "chi2";
			}
		};
	}
	
	public static RegPar logit() {
		return new RegPar() {
			
			@Override
			public String getStatName() {
				return "Wald chi2";
			}
			
			@Override
			public String getStatId() {
				return "chi2";
			}
		};
	}
	
}