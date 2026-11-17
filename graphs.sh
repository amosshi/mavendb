#!/bin/sh
#
# Dependency graphs of mavendb-loader, generated from mavendb-loader/target/classes: run after 'compile'.
# Used by build.sh and the GitHub Actions workflows. Needs jdeps (JDK) and dot (Graphviz).
set -e
cd "$(dirname "$0")"

# Class dependency graph (packaged into the dist zip as doc/project.svg); -e keeps only org.mavendb classes
jdeps -verbose:class -filter:none -e 'org\.mavendb\..*' -dotoutput mavendb-loader/target/jdeps mavendb-loader/target/classes
dot -Tsvg mavendb-loader/target/jdeps/classes.dot -o mavendb-loader/target/jdeps/project.svg

# Module graph (embedded in the javadoc of package org.mavendb.loader): mavendb-loader -> 3rd party jars and JDK modules.
# The javadoc plugin copies mavendb-loader/target/javadoc-resources (its javadocDirectory) into the javadoc output.
./mvnw -q -B -ntp -pl mavendb-loader dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/jdeps/classpath.txt
jdeps --multi-release 25 --ignore-missing-deps -summary -dotoutput mavendb-loader/target/jdeps/modules \
    --class-path "$(cat mavendb-loader/target/jdeps/classpath.txt)" mavendb-loader/target/classes
mkdir -p mavendb-loader/target/javadoc-resources/org/mavendb/loader/doc-files
sed -e 's/"classes"/"mavendb-loader"/' -e 's/ ([a-z.]*)"/"/' mavendb-loader/target/jdeps/modules/summary.dot \
    | dot -Grankdir=LR -Nshape=box -Nfontname=Helvetica -Tsvg -o mavendb-loader/target/javadoc-resources/org/mavendb/loader/doc-files/module-graph.svg
