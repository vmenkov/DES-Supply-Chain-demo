#!/bin/csh
#----------------------------------------------------
# Sample usage:
# ./run.sh -until 300
#----------------------------------------------------


set h=`(cd ..; pwd)`
setenv CLASSPATH $h/work/lib/demo.jar:$h/lib/'*'

#echo java -cp $cp edu.rutgers.pharma.Demo
#java edu.rutgers.pharma.Demo -until 300
java edu.rutgers.pharma.Demo  $argv[1-]

#-time 10
