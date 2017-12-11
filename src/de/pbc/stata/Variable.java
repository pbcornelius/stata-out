package de.pbc.stata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.stata.sfi.Data;
import com.stata.sfi.SFIToolkit;

public class Variable {
	
	// CONSTANT ----------------------------------------------------- //
	
	private static final Pattern indicator = Pattern.compile("^(\\d*)(?:c?)(o?)(b?)(l(\\d*))?(d?)\\.(.+)");
	
	// VARIABLES ---------------------------------------------------- //
	
	private String name;
	
	private String format;
	
	private Integer value;
	
	private boolean omitted = false;
	
	private boolean base = false;
	
	private boolean lagged = false;
	
	private Integer lag = 1;
	
	private boolean delta = false;
	
	// CONSTRUCTOR -------------------------------------------------- //
	
	public Variable(String name) {
		this(name, null);
	}
	
	public Variable(String name, String format) {
		name = name.toLowerCase();
		Matcher m;
		if ((m = indicator.matcher(name)).matches()) {
			value = m.group(1).length() > 0 ? Integer.valueOf(m.group(1)) : null;
			omitted = m.group(2).equalsIgnoreCase("o");
			base = m.group(3).equalsIgnoreCase("b");
			lagged = m.group(4) != null;
			if (lagged && m.group(5).length() > 0)
				lag = Integer.valueOf(m.group(5));
			delta = m.group(6).equalsIgnoreCase("d");
			this.name = m.replaceAll("$7");
		} else {
			this.name = name;
		}
		
		if (format == null)
			this.format = Data.getVarFormat(getIndex());
		else
			this.format = format;
	}
	
	// PUBLIC ------------------------------------------------------- //
	
	public String getName() {
		return name;
	}
	
	public Integer getIndex() {
		return Data.getVarIndex(name);
	}
	
	public boolean hasIndex() {
		return getIndex() <= Data.getVarCount();
	}
	
	public String getLabel() {
		if (hasIndex()) {
			String label = Data.getVarLabel(getIndex());
			
			if (label.isEmpty())
				label = name;
			
			if (value != null && format == null)
				label += " = " + value;
			else if (value != null && format != null)
				label += " = " + SFIToolkit.formatValue(value, format).trim();
			
			if (lagged)
				label += " (t - " + lag + ")";
			
			if (delta)
				label += " (delta)";
			
			return label;
		} else {
			if (name.equals("_cons"))
				return "constant";
			else
				return name;
		}
	}
	
	public boolean isOmitted() {
		return omitted;
	}
	
	public boolean isBase() {
		return base;
	}
	
	public String toString() {
		return getLabel();
	}
	
}