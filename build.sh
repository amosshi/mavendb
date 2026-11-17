#!/bin/sh
#

rm -rf mavendb-loader/dist/

# Compile first: the dependency graphs are generated from the target/classes of each module,
# and must exist before 'site' builds the javadoc
./mvnw clean compile -e || exit 1
./graphs.sh || exit 1

./mvnw package test install -e
# Maven site of all modules, with the javadoc of all modules at target/staging/apidocs
./mvnw site site:stage

# 3rd party dependencies
./mvnw dependency:copy-dependencies dependency:tree versions:display-dependency-updates

cd mavendb-loader/dist && unzip *.zip
