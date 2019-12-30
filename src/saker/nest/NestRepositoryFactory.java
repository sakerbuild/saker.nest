package saker.nest;

import java.util.ServiceLoader;

import saker.apiextract.api.PublicApi;
import saker.build.runtime.repository.RepositoryEnvironment;
import saker.build.runtime.repository.SakerRepository;
import saker.build.runtime.repository.SakerRepositoryFactory;

/**
 * The {@link SakerRepositoryFactory} implementation for the Nest repository.
 * <p>
 * This is the main entry point that is used to create a {@link SakerRepository} instance.
 */
@PublicApi
public final class NestRepositoryFactory implements SakerRepositoryFactory {
	/**
	 * The default identifier for the Nest repository.
	 */
	public static final String IDENTIFIER = "nest";

	/**
	 * Instantiates the repository factory.
	 * <p>
	 * As the repository factory is stateless, an instance is only required for the {@link ServiceLoader}
	 * implementation, and to call {@link #create(RepositoryEnvironment)}.
	 */
	public NestRepositoryFactory() {
	}

	@Override
	public SakerRepository create(RepositoryEnvironment environment) {
		return new NestRepositoryImpl(environment);
	}
}