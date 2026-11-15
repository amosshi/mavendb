#!/bin/sh
#

rm -rf mavendb-loader/dist/

# Compile first: the class dependency graph is generated from mavendb-loader/target/classes,
# and must exist before 'package' builds the dist zip
./mvnw clean compile -e || exit 1

# Class dependency graph (packaged into the dist zip as doc/project.svg); -e keeps only org.mavendb classes
jdeps -verbose:class -filter:none -e 'org\.mavendb\..*' -dotoutput mavendb-loader/target/jdeps mavendb-loader/target/classes || exit 1
dot -Tsvg mavendb-loader/target/jdeps/classes.dot -o mavendb-loader/target/jdeps/project.svg || exit 1

./mvnw package test install -e

# Generate javadoc
#./mvnw javadoc:javadoc

# 3rd party dependencies
./mvnw dependency:copy-dependencies dependency:tree versions:display-dependency-updates

cd mavendb-loader/dist && unzip *.zip
