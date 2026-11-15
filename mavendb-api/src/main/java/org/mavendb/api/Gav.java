package org.mavendb.api;

/**
 * One file of a Maven artifact version.
 *
 * @param groupId       Group, like <code>org.apache.commons</code>
 * @param artifactId    Artifact, like <code>commons-lang3</code>
 * @param version       Version, like <code>3.12.0</code>
 * @param classifier    Classifier, like <code>sources</code>; null for the main file
 * @param packaging     Packaging, like <code>jar</code>
 * @param fileExtension File extension, like <code>jar</code>
 * @param fileName      File name, like <code>commons-lang3-3.12.0.jar</code>
 * @param sha1          SHA-1 of the file
 * @param fileSize      Size in bytes
 * @param fileModified  Last modified, milliseconds since 1970-01-01 UTC
 */
public record Gav(String groupId, String artifactId, String version, String classifier, String packaging,
                  String fileExtension, String fileName, String sha1, Long fileSize, Long fileModified) {
}
