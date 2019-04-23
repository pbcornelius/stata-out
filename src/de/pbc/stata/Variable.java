package de.pbc.stata;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.stata.sfi.Data;
import com.stata.sfi.SFIToolkit;
import com.stata.sfi.ValueLabel;

public class Variable {
	
	// CONSTANT ----------------------------------------------------- //
	
	private static final Pattern FLAGS = Pattern.compile(
			"^(?<flags>([cobid]|(?<val>\\d+)|(?<lag>l\\d*))*)\\.(?<varname>.+)$");
	
	// VARIABLES ---------------------------------------------------- //
	
	private String name;
	
	private String format;
	
	private String valueLabel;
	
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
		if ((m = FLAGS.matcher(name)).matches()) {
			if (m.group("val") != null)
				value = Integer.valueOf(m.group("val"));
			
			if (m.group("lag") != null) {
				lagged = true;
				lag = m.group("lag").replace("l", "").isEmpty() ? 1 : Integer.valueOf(m.group("lag").replace("l", ""));
			}
			
			String flags = m.group("flags");
			
			omitted = flags.contains("o");
			base = flags.contains("b");
			delta = flags.contains("d");
			
			this.name = m.group("varname");
		} else {
			this.name = name;
		}
		
//		SFIToolkit.displayln(String.format("%1$s o:%2$b b:%3$b d:%4$b", this.name, omitted, base, delta));
		
		if (format == null)
			this.format = Data.getVarFormat(getIndex());
		else
			this.format = format;
		
		valueLabel = ValueLabel.getVarValueLabel(getIndex());
		if (valueLabel != null && valueLabel.length() == 0)
			valueLabel = null;
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
		if (name.equals("_cons")) {
			return "constant";
		} else if (hasIndex()) {
			/*
			 * Sometimes, Stata returns a non-empty label string, even when
			 * there is no label defined in Stata. In this case, one can (1) try
			 * and remove the invalid label in Stata or (2) disable the variable
			 * labels here.
			 * This functionality is disabled until Stata fixes this.
			 */
//			String varLabel = Data.getVarLabel(getIndex());
			String varLabel = "";
			
			if (varLabel.isEmpty()) {
				varLabel = name;
			}
			
			if (value != null && (format == null || format.isEmpty()) && valueLabel == null)
				varLabel += " = " + value;
			else if (value != null && valueLabel != null)
				varLabel += " = " + ValueLabel.getLabel(valueLabel, value);
			else if (value != null && format != null && !format.isEmpty()) {
				varLabel += " = " + SFIToolkit.formatValue(value, format).trim();
			}
			
			if (lagged)
				varLabel += " (t - " + lag + ")";
			
			if (delta)
				varLabel += " (delta)";
			
			return varLabel;
		} else {
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