package org.mavendb.api;

/**
 * An artifact of a group.
 *
 * @param artifactId      Artifact, like <code>commons-lang3</code>
 * @param versionCount    Number of versions
 * @param fileModifiedMax Newest file, milliseconds since 1970-01-01 UTC
 */
public record Artifact(String artifactId, long versionCount, Long fileModifiedMax) {
}
