#!/bin/bash

USAGE="usage: linux.sh [compile] [clean] [execute [federate-name]]"

################################
# check command line arguments #
################################
if [ $# = 0 ]
then
	echo $USAGE
	exit;
fi

######################
# test for JAVA_HOME #
######################
JAVA=java
if [ "$JAVA_HOME" = "" ]
then
	echo WARNING Your JAVA_HOME environment variable is not set!
	#exit;
else
        JAVA=$JAVA_HOME/bin/java
fi

#####################
# test for RTI_HOME #
#####################
if [ "$RTI_HOME" = "" ]
then
	cd ../../../
	RTI_HOME=$PWD
	export RTI_HOME
	cd examples/java/zzzexample
	echo WARNING Your RTI_HOME environment variable is not set, assuming $RTI_HOME
fi

############################################
### (target) clean #########################
############################################
if [ $1 = "clean" ]
then
	echo "deleting zzzexample federate jar file and left over logs"
	rm src/zzzexample/*.class
	rm java-zzzexample.jar
	rm -Rf logs
	exit;
fi

############################################
### (target) compile #######################
############################################
if [ $1 = "compile" ]
then
	echo "compiling zzzexample federate"
	cd src
	javac -cp ./:$RTI_HOME/lib/portico.jar zzzexample/*.java
	jar -cf ../java-zzzexample.jar zzzexample/*.class
	cd ../
	exit;	
fi

############################################
### (target) execute #######################
############################################
if [ $1 = "execute" ]
then
	shift;
	java -cp ./java-zzzexample.jar:$RTI_HOME/lib/portico.jar zzzexample.ExampleFederate $*
	exit;
fi

echo $USAGE

