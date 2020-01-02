/*
 * Copyright (C) 2020 Bence Sipka
 *
 * This program is free software: you can redistribute it and/or modify 
 * it under the terms of the GNU General Public License as published by 
 * the Free Software Foundation, version 3.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package saker.nest;

import java.io.IOException;
import java.util.NavigableSet;
import java.util.Objects;

import saker.build.runtime.repository.BuildRepository;
import saker.build.runtime.repository.RepositoryBuildEnvironment;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.task.TaskFactory;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolver;
import saker.build.thirdparty.saker.util.classloader.ClassLoaderResolverRegistry;
import saker.build.thirdparty.saker.util.classloader.SingleClassLoaderResolver;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.NestBundleClassLoader;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.NestRepositoryBundleClassLoader;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.scriptinfo.reflection.ReflectionExternalScriptInformationProvider;
import testing.saker.nest.TestFlag;

public class NestBuildRepositoryImpl implements BuildRepository {
	private final RepositoryBuildEnvironment environment;

	private final ConfiguredRepositoryStorage configuredStorage;

	private final String coreClassLoaderResolverId;
	private final String bundlesClassLoaderResolverId;
	private final SingleClassLoaderResolver coreClassLoaderResolver = new SingleClassLoaderResolver("classes",
			NestBuildRepositoryImpl.class.getClassLoader());
	private final ClassLoaderResolver bundlesClassLoaderResolver = new ClassLoaderResolver() {
		@Override
		public String getClassLoaderIdentifier(ClassLoader classloader) {
			if (classloader instanceof NestBundleClassLoader) {
				NestBundleClassLoader nestbundlecl = (NestBundleClassLoader) classloader;
				NestRepositoryBundle nestbundle = nestbundlecl.getBundle();
				BundleIdentifier bundleid = nestbundle.getBundleIdentifier();
				String storageid = configuredStorage.getStorageIdentifier(nestbundlecl);
				if (storageid != null) {
					String hash = StringUtils.toHexString(nestbundlecl.getBundleHashWithClassPathDependencies());
					return storageid + NestRepositoryImpl.CHAR_CL_IDENITIFER_SEPARATOR + bundleid.toString()
							+ NestRepositoryImpl.CHAR_CL_IDENITIFER_SEPARATOR + hash;
				}
			}
			return null;
		}

		@Override
		public ClassLoader getClassLoaderForIdentifier(String identifier) {
			int idx = identifier.indexOf(NestRepositoryImpl.CHAR_CL_IDENITIFER_SEPARATOR);
			int idx2 = identifier.indexOf(NestRepositoryImpl.CHAR_CL_IDENITIFER_SEPARATOR, idx + 1);
			String storageid = identifier.substring(0, idx);
			String bundleidstr = identifier.substring(idx + 1, idx2);

			BundleIdentifier bundleid;
			try {
				bundleid = BundleIdentifier.valueOf(bundleidstr);
			} catch (IllegalArgumentException e) {
				return null;
			}

			try {
				NestRepositoryBundleClassLoader bundlecl = configuredStorage
						.getBundleClassLoaderForStorageIdentifier(storageid, bundleid);
				if (bundlecl == null) {
					return null;
				}
				if (Objects.equals(identifier.substring(idx2 + 1),
						StringUtils.toHexString(bundlecl.getSharedBundleHashWithClassPathDependencies()))) {
					return bundlecl;
				}
			} catch (BundleLoadingFailedException e) {
				System.err.println("Failed to load bundle: " + bundleid);
			}
			return null;
		}
	};

	public static NestBuildRepositoryImpl create(NestRepositoryImpl nestRepository,
			RepositoryBuildEnvironment environment) {
		return new NestBuildRepositoryImpl(nestRepository, environment);
	}

	private NestBuildRepositoryImpl(NestRepositoryImpl nestRepository, RepositoryBuildEnvironment environment) {
		this.environment = environment;

		coreClassLoaderResolverId = nestRepository.getCoreClassLoaderResolverId(environment.getIdentifier());
		bundlesClassLoaderResolverId = coreClassLoaderResolverId + "/bundles";

		ClassLoaderResolverRegistry clregistry = environment.getClassLoaderResolverRegistry();
		clregistry.register(coreClassLoaderResolverId, coreClassLoaderResolver);
		clregistry.register(bundlesClassLoaderResolverId, bundlesClassLoaderResolver);

		configuredStorage = createConfiguredRepository(nestRepository, environment);
	}

	private static ConfiguredRepositoryStorage createConfiguredRepository(NestRepositoryImpl nestRepository,
			RepositoryBuildEnvironment environment) {
		if (TestFlag.ENABLED) {
			ConfiguredRepositoryStorage cached = (ConfiguredRepositoryStorage) TestFlag.metric()
					.retrieveCachedConfiguredRepository(nestRepository, environment.getIdentifier(),
							environment.getPathConfiguration(), environment.getUserParameters());
			if (cached != null) {
				return cached;
			}
		}
		return new ConfiguredRepositoryStorage(nestRepository, environment.getIdentifier(),
				environment.getPathConfiguration(), environment.getUserParameters());
	}

	public RepositoryBuildEnvironment getBuildEnvironment() {
		return environment;
	}

	public NestRepository getRepository() {
		return configuredStorage.getRepository();
	}

	public ConfiguredRepositoryStorage getConfiguredStorage() {
		return configuredStorage;
	}

	@Override
	public ExternalScriptInformationProvider getScriptInformationProvider() {
		return new ReflectionExternalScriptInformationProvider(this);
	}

	public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
		return configuredStorage.getPresentTaskNamesForInformationProvider();
	}

	public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
		return configuredStorage.getPresentBundlesForInformationProvider();
	}

	@Override
	public Object detectChanges() {
		return configuredStorage.detectChanges(environment.getPathConfiguration());
	}

	@Override
	public void handleChanges(Object detectedchanges) {
		configuredStorage.handleChanges(environment.getPathConfiguration(), detectedchanges);
	}

	@Override
	public TaskFactory<?> lookupTask(TaskName taskname) throws TaskNotFoundException {
		return configuredStorage.lookupTask(taskname);
	}

	public Class<?> getTaskClassForInformationProvider(TaskName taskname) {
		return configuredStorage.getTaskClassForInformationProvider(taskname);
	}

	@Override
	public void close() throws IOException {
		ClassLoaderResolverRegistry clregistry = environment.getClassLoaderResolverRegistry();
		clregistry.unregister(bundlesClassLoaderResolverId, bundlesClassLoaderResolver);
		clregistry.unregister(coreClassLoaderResolverId, coreClassLoaderResolver);

		closeConfiguredRepository();
	}

	private void closeConfiguredRepository() throws IOException {
		if (TestFlag.ENABLED) {
			if (TestFlag.metric().offerConfiguredRepositoryCache(configuredStorage.getRepository(),
					environment.getIdentifier(), environment.getPathConfiguration(), environment.getUserParameters(),
					configuredStorage)) {
				//cached, don't close
				return;
			}
		}
		configuredStorage.close();
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + environment.getIdentifier() + "]";
	}
}