capture program drop regout2
capture program regout2
	version 15
	syntax [, args(string asis)]
	
	javacall de.pbc.stata.RegOut2 start, jars(commons-io-2.4.jar poi-5.0.0.jar poi-ooxml-5.0.0.jar xmlbeans-5.0.2.jar commons-collections4-4.4.jar commons-compress-1.21.jar poi-ooxml-full-5.0.0.jar log4j-api-2.14.1.jar log4j-core-2.14.1.jar commons-math3-3.6.1.jar) classpath("C:\Users\corne\Git\stata-out\bin;C:\Users\corne\Git\stata-utils\bin") args(`args')
end