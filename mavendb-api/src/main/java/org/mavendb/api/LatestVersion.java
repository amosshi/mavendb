package org.mavendb.api;

/**
 * The latest version of an artifact.
 *
 * @param groupId      Group
 * @param artifactId   Artifact
 * @param version      Highest version
 * @param fileModified Last modified of that version's file, milliseconds since 1970-01-01 UTC
 */
public record LatestVersion(String groupId, String artifactId, String version, Long fileModified) {
}
