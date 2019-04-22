#!/bin/bash


cd /app
JAVACMD="java -jar -Dconf=config.edn -Dlogback.configurationFile=logging.xml"
$JAVACMD kti.jar migrate && $JAVACMD kti.jar
