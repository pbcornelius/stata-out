package de.pbc.stata;

import java.util.Objects;

import com.stata.sfi.Data;
import com.stata.sfi.Scalar;

public class ModelStat {
	
	// VARIABLES ---------------------------------------------------- //
	
	protected String local, localP, label;
	
	protected Double val, p;
	
	protected int defScale;
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	protected ModelStat(String local, String localP, String label) {
		this(local, localP, label, 2);
	}
	
	protected ModelStat(String local, String localP, String label, int defScale) {
		this.local = local;
		this.localP = localP;
		this.label = label;
		this.defScale = defScale;
		
		getValues();
	}
	
	// PROTECTED ---------------------------------------------------- //
	
	protected void getValues() {
		val = Scalar.getValue(local, Scalar.TYPE_ERETURN);
		val = Data.isValueMissing(val) ? null : val;
		
		if (Objects.nonNull(localP)) {
			p = Scalar.getValue(localP, Scalar.TYPE_ERETURN);
			if (Objects.nonNull(p)) {
				p = Data.isValueMissing(p) ? null : p;
			}
		}
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public String getName() {
		return local;
	}
	
	public String getP() {
		return localP;
	}
	
	public String getLabel() {
		return label;
	}
	
	public String getVal() {
		return getVal(defScale);
	}
	
	public String getVal(int scale) {
		if (Objects.nonNull(val)) {
			return StataUtils.correctRounding(val, scale);
		} else {
			return "";
		}
	}
	
	public String getSigStars() {
		if (Objects.nonNull(p)) {
			return Term.SIG_LEVELS.apply(p);
		} else {
			return "";
		}
	}
	
	@Override
	public String toString() {
		return getVal() + getSigStars();
	}
	
}