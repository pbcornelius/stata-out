package de.pbc.stata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.stata.sfi.Data;

public interface Variable {
	
	// PUBLIC ------------------------------------------------------- //
	
	public String getName();
	
	public Integer getIndex();
	
	public String getLabel();
	
	public boolean isBase();
	
	// FACTORY ------------------------------------------------------ //
	
	public static Variable create(String name) {
		return new VariableImpl(name);
	}
	
	// INNER CLASSES ------------------------------------------------ //
	
	static class VariableImpl implements Variable {
		
		private Pattern indicator = Pattern.compile("^(\\d+)(b?)\\.(.+)");
		
		// VARIABLES ------------------------------------------------ //
		
		private String name;
		
		private Integer value;
		
		private boolean base = false;
		
		// CONSTRUCTOR ---------------------------------------------- //
		
		public VariableImpl(String name) {
			Matcher m;
			if (name.startsWith("c.")) {
				this.name = name.replace("c.", "");
			} else if ((m = indicator.matcher(name)).matches()) {
				value = Integer.valueOf(m.group(1));
				base = m.group(2).equals("b");
				this.name = m.replaceAll("$3");
			} else {
				this.name = name;
			}
		}
		
		// PUBLIC --------------------------------------------------- //
		
		@Override
		public String getName() {
			return name;
		}
		
		@Override
		public Integer getIndex() {
			return Data.getVarIndex(name);
		}
		
		public boolean hasIndex() {
			return getIndex() <= Data.getVarCount();
		}
		
		@Override
		public String getLabel() {
			if (hasIndex()) {
				String label = Data.getVarLabel(getIndex());
				if (label.isEmpty())
					label = name;
				if (value != null)
					label += "=" + value;
				return label;
			} else {
				if (name.equals("_cons"))
					return "constant";
				else
					return name;
			}
		}
		
		@Override
		public boolean isBase() {
			return base;
		}
		
		@Override
		public String toString() {
			return getLabel();
		}
		
	}
	
}