package saker.nest.bundle;

import java.nio.file.Path;

import saker.apiextract.api.PublicApi;

/**
 * {@link NestRepositoryBundle} that is backed by a Java ARchive file.
 * <p>
 * The {@link #getJarPath()} can be used to retrieve the path to the JAR file.
 */
@PublicApi
public interface JarNestRepositoryBundle extends NestRepositoryBundle {
	/**
	 * Gets the JAR file path that this bundle is backed by.
	 * 
	 * @return The absolute path to the JAR.
	 */
	public Path getJarPath();
}
