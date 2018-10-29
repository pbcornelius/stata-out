package de.pbc.stata;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Term {
	
	// CONSTANTS ---------------------------------------------------- //
	
	private static final Function<Double, String> SIG_LEVELS = (p) -> p < .01
			? "***"
			: p < .05 ? "**" : p < .1 ? "*" : "";
	
	// VARIABLES ---------------------------------------------------- //
	
	private int index;
	
	private String name;
	
	private List<Variable> vars;
	
	private Double coef;
	
	private Double se;
	
	private Double p;
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	public Term(int index, String name) {
		this(index, name, null, null, null);
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public Term(int index, String name, Double coef, Double se, Double p) {
		this.index = index;
		this.name = name.trim();
		vars = Arrays.stream(this.name.split("#")).map(Variable::new).collect(Collectors.toList());
		this.coef = coef;
		this.se = se;
		this.p = p;
	}
	
	public int getIndex() {
		return index;
	}
	
	public String getName() {
		return name;
	}
	
	public String getLabel() {
		return vars.stream().collect(Collectors.groupingBy((e) -> e.getName())).entrySet().stream().map((e) -> {
			int power = e.getValue().size();
			if (power <= 3)
				return e.getValue().get(0).getLabel() + (power == 1 ? "" : power == 2 ? "²" : "³");
			else
				return e.getValue().stream().map((e1) -> e1.getLabel()).collect(Collectors.joining(" * "));
		}).collect(Collectors.joining(" * "));
	}
	
	public List<Variable> getVariables() {
		return vars;
	}
	
	public boolean isOmitted() {
		return vars.stream().anyMatch((v) -> v.isOmitted());
	}
	
	public boolean isBase() {
		return vars.stream().anyMatch((v) -> v.isBase());
	}
	
	public String getCoefficient(int scale) {
		return StataUtils.correctRounding(coef, scale);
	}
	
	public String getStandardError(int scale) {
		return StataUtils.correctRounding(se, scale);
	}
	
	public String getSigStars() {
		return SIG_LEVELS.apply(p);
	}
	
	public String toString() {
		return getLabel();
	}
	
}