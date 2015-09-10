package de.pbc.stata;

public interface RegPar {
	
	// PUBLIC ------------------------------------------------------- //
	
	public default boolean isTwoStages() {
		return false;
	}
	
	public default boolean hasGroups() {
		return false;
	}
	
	public String getStatName();
	
	public String getStatId();
	
}