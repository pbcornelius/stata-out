capture program regout
	version 15
	syntax [, args(string asis)]
	
	javacall de.pbc.stata.RegOut start, jars(commons-io-2.4.jar poi-3.11-20141221.jar poi-ooxml-3.11-20141221.jar poi-ooxml-schemas-3.11-20141221.jar xmlbeans-2.6.0.jar) classpath("C:\Users\corne\Git\stata-out\bin;C:\Users\corne\Git\stata-utils\bin") args(`args')
end