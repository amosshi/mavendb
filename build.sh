#!/bin/sh
#

rm -rf mavendb-loader/dist/

# Compile first: the class dependency graph is generated from mavendb-loader/target/classes,
# and must exist before 'package' builds the dist zip
./mvnw clean compile -e || exit 1

# Class dependency graph (packaged into the dist zip as doc/project.svg); -e keeps only org.mavendb classes
jdeps -verbose:class -filter:none -e 'org\.mavendb\..*' -dotoutput mavendb-loader/target/jdeps mavendb-loader/target/classes || exit 1
dot -Tsvg mavendb-loader/target/jdeps/classes.dot -o mavendb-loader/target/jdeps/project.svg || exit 1

# Module graph (embedded in the javadoc of package org.mavendb.loader): mavendb-loader -> 3rd party jars and JDK modules.
# The javadoc plugin copies mavendb-loader/target/javadoc-resources (its javadocDirectory) into the javadoc output.
./mvnw -q -pl mavendb-loader dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/jdeps/classpath.txt || exit 1
jdeps --multi-release 25 --ignore-missing-deps -summary -dotoutput mavendb-loader/target/jdeps/modules \
    --class-path "$(cat mavendb-loader/target/jdeps/classpath.txt)" mavendb-loader/target/classes || exit 1
mkdir -p mavendb-loader/target/javadoc-resources/org/mavendb/loader/doc-files
sed -e 's/"classes"/"mavendb-loader"/' -e 's/ ([a-z.]*)"/"/' mavendb-loader/target/jdeps/modules/summary.dot \
    | dot -Grankdir=LR -Nshape=box -Nfontname=Helvetica -Tsvg -o mavendb-loader/target/javadoc-resources/org/mavendb/loader/doc-files/module-graph.svg || exit 1

./mvnw package test install -e
./mvnw javadoc:javadoc

# 3rd party dependencies
./mvnw dependency:copy-dependencies dependency:tree versions:display-dependency-updates

cd mavendb-loader/dist && unzip *.zip
