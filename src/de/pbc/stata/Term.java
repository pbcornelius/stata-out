package de.pbc.stata;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public interface Term {
	
	// PUBLIC ------------------------------------------------------- //
	
	public int getIndex();
	
	public String getName();
	
	public String getLabel();
	
	public List<Variable> getVariables();
	
	public boolean isOmitted();
	
	public boolean isBase();
	
	// FACTORY ------------------------------------------------------ //
	
	public static Term create(int index, String name) {
		return new TermImpl(index, name);
	}
	
	// INNER CLASSES ------------------------------------------------ //
	
	static class TermImpl implements Term {
		
		// VARIABLES ---------------------------------------------------- //
		
		private int index;
		
		private String name;
		
		private List<Variable> vars;
		
		// CONSTRUCTOR -------------------------------------------------- //
		
		public TermImpl(int index, String name) {
			this.index = index;
			this.name = name.trim();
			vars = Arrays.stream(this.name.split("#")).map(Variable::create).collect(Collectors.toList());
		}
		
		// PUBLIC ------------------------------------------------------- //
		
		@Override
		public int getIndex() {
			return index;
		}
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public String getLabel() {
			return vars.stream().collect(Collectors.groupingBy((e) -> e.getName())).entrySet().stream().map((e) -> {
				int power = e.getValue().size();
				if (power <= 3)
					return e.getValue().get(0).getLabel() + (power == 1 ? "" : power == 2 ? "²" : "³");
				else
					return e.getValue().stream().map((e1) -> e1.getLabel()).collect(Collectors.joining(" * "));
			}).collect(Collectors.joining(" * "));
		}
		
		@Override
		public List<Variable> getVariables() {
			return vars;
		}
		
		@Override
		public boolean isOmitted() {
			return vars.stream().anyMatch((v) -> v.isOmitted());
		}
		
		@Override
		public boolean isBase() {
			return vars.stream().anyMatch((v) -> v.isBase());
		}
		
		@Override
		public String toString() {
			return getLabel();
		}
		
	}
	
}