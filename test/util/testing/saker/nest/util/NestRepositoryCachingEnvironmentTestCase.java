package testing.saker.nest.util;

import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import saker.build.file.path.PathKey;
import saker.build.file.path.ProviderHolderPathKey;
import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.LocalFileProvider;
import saker.build.file.provider.SakerFileProvider;
import saker.build.file.provider.SakerPathFiles;
import saker.build.runtime.params.ExecutionPathConfiguration;
import testing.saker.build.tests.EnvironmentTestCase;
import testing.saker.nest.NestMetric;
import testing.saker.nest.util.RepositoryLoadingVariablesMetricEnvironmentTestCase;

public abstract class NestRepositoryCachingEnvironmentTestCase
		extends RepositoryLoadingVariablesMetricEnvironmentTestCase {
	private NestMetric hashCacherMetric;

	@Override
	protected boolean isRepositoriesCacheable() {
		return true;
	}

	@Override
	protected final void runTestImpl() throws Throwable {
		hashCacherMetric = new RepositoryCachingNestMetric();
		testing.saker.nest.TestFlag.set(hashCacherMetric);
		runNestTaskTestImpl();
	}

	protected abstract void runNestTaskTestImpl() throws Throwable;

	private static final Object MARKER_NO_MORE_REPOSITORY_CACHING = new Object();
	private static Object cachedConfiguredRepositoryRepository;
	private static String cachedConfiguredRepositoryIdentifier;
	private static ExecutionPathConfiguration cachedConfiguredRepositoryPathConfiguration;
	private static Map<String, String> cachedConfiguredRepositoryUserParameters;
	private static Object cachedConfiguredRepositoryConfiguredRepository;

	static {
		EnvironmentTestCase.addCloseable(() -> {
			synchronized (RepositoryCachingNestMetric.class) {
				Object confrepo = cachedConfiguredRepositoryConfiguredRepository;
				cachedConfiguredRepositoryRepository = MARKER_NO_MORE_REPOSITORY_CACHING;
				cachedConfiguredRepositoryIdentifier = null;
				cachedConfiguredRepositoryPathConfiguration = null;
				cachedConfiguredRepositoryUserParameters = null;
				cachedConfiguredRepositoryConfiguredRepository = null;
				if (confrepo != null) {
					((Closeable) confrepo).close();
				}
			}
		});
	}

	private final class RepositoryCachingNestMetric implements NestMetric {
		private Map<PathKey, FileHashResult> hashes = new ConcurrentHashMap<>();

		public RepositoryCachingNestMetric() {
		}

		@Override
		public FileHashResult overrideParameterBundlePerceivedHash(SakerFileProvider fp, SakerPath bundlepath,
				String algorithm) throws NoSuchAlgorithmException, IOException {
			ProviderHolderPathKey pk = SakerPathFiles.getPathKey(fp, bundlepath);
			FileHashResult got = hashes.get(pk);
			if (got != null) {
				return got;
			}
			if (LocalFileProvider.getProviderKeyStatic().equals(pk.getFileProviderKey())
					&& repositoryParameterBundlePaths.contains(pk.getPath())) {
				FileHashResult hash = fp.hash(bundlepath, algorithm);
				hashes.put(pk, hash);
				return hash;
			}
			return null;
		}

		@Override
		public boolean offerConfiguredRepositoryCache(Object repositoryobj, String repoid,
				ExecutionPathConfiguration pathconfig, Map<String, String> userparameters, Object configuredstorage) {
			synchronized (RepositoryCachingNestMetric.class) {
				if (cachedConfiguredRepositoryRepository == MARKER_NO_MORE_REPOSITORY_CACHING) {
					return false;
				}
				if (configuredstorage == cachedConfiguredRepositoryConfiguredRepository) {
					return true;
				}
				if (cachedConfiguredRepositoryConfiguredRepository != null) {
					try {
						((Closeable) cachedConfiguredRepositoryConfiguredRepository).close();
					} catch (IOException e) {
						throw new UncheckedIOException(e);
					}
				}
				cachedConfiguredRepositoryRepository = repositoryobj;
				cachedConfiguredRepositoryIdentifier = repoid;
				cachedConfiguredRepositoryPathConfiguration = pathconfig;
				cachedConfiguredRepositoryUserParameters = userparameters;
				cachedConfiguredRepositoryConfiguredRepository = configuredstorage;
			}
			return true;
		}

		@Override
		public Object retrieveCachedConfiguredRepository(Object repositoryobj, String repoid,
				ExecutionPathConfiguration pathconfig, Map<String, String> userparameters) {
			synchronized (RepositoryCachingNestMetric.class) {
				if (cachedConfiguredRepositoryRepository == MARKER_NO_MORE_REPOSITORY_CACHING) {
					return null;
				}
				if (repositoryobj == cachedConfiguredRepositoryRepository) {
					if (repoid.equals(cachedConfiguredRepositoryIdentifier)
//							&& pathconfig.equals(cachedConfiguredRepositoryPathConfiguration)
							&& userparameters.equals(cachedConfiguredRepositoryUserParameters)) {
						return cachedConfiguredRepositoryConfiguredRepository;
					}
				}
			}
			return null;
		}
	}

}
