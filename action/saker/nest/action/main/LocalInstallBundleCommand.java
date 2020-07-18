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
package saker.nest.action.main;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.file.path.WildcardPath;
import saker.build.file.path.WildcardPath.ItemLister;
import saker.build.file.provider.LocalFileProvider;
import saker.build.runtime.execution.SakerLog;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.util.io.ByteSink;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorageView.InstallResult;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.PositionalParameter;

/**
 * <pre>
 * Installs the specified bundle(s) to the local bundle storage.
 * 
 * The command will take the specified bundles and place them
 * in the local bundle storage. Other agents in the system will
 * be able to use these installed bundles.
 * 
 * Any already existing bundle(s) with the same identifier will
 * be overwritten.
 * </pre>
 */
public class LocalInstallBundleCommand {
	/**
	 * <pre>
	 * One or more paths to the bundles that should be installed.
	 * 
	 * The paths can also be wildcards, e.g. *.jar to install
	 * all Java archives in the current directory as saker.nest bundles.
	 * </pre>
	 */
	@Parameter
	@PositionalParameter(value = -1)
	@Converter(method = "parseRemainingCommand")
	public Set<String> bundles = new LinkedHashSet<>();

	/**
	 * <pre>
	 * Specifies the identifier of the repository.
	 * 
	 * The identifier is used to properly determine the 
	 * configuration user parameters from the -U arguments.
	 * 
	 * It is "nest" by default.
	 * </pre>
	 */
	@Parameter("-repo-id")
	public String repositoryId = NestRepositoryFactory.IDENTIFIER;

	/**
	 * <pre>
	 * Sets the name of the local bundle storage to where the bundle(s)
	 * should be installed.
	 * 
	 * It is "local" by default.
	 * </pre>
	 */
	@Parameter("-storage")
	public String storage = ConfiguredRepositoryStorage.STORAGE_TYPE_LOCAL;

	private Map<String, String> userParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Specifies the user parameters for configuring the repository.
	 * 
	 * This string key-value pairs are interpreted the same way as the
	 * -U user parameters for the build execution.
	 * </pre>
	 */
	@Parameter("-U")
	public void userParameter(String key, String value) {
		if (userParameters.containsKey(key)) {
			throw new IllegalArgumentException("User parameter specified multiple times: " + key);
		}
		userParameters.put(key, value);
	}

	public void call(ExecuteActionCommand execute) throws InvalidPathFormatException, IOException {
		Set<WildcardPath> wildcards = new LinkedHashSet<>();
		for (String b : bundles) {
			WildcardPath wp = WildcardPath.valueOf(b);
			wildcards.add(wp);
		}
		SakerPath workingdir = SakerPath.valueOf(System.getProperty("user.dir"));
		LocalFileProvider localfp = LocalFileProvider.getInstance();
		NavigableMap<SakerPath, ? extends BasicFileAttributes> bundles = WildcardPath.getItems(wildcards,
				ItemLister.forFileProvider(localfp, workingdir));
		if (bundles.isEmpty()) {
			System.out.println("No bundles found.");
			return;
		}
		try (ConfiguredRepositoryStorage configuredstorage = ConfiguredRepositoryStorage.forRepositoryAction(
				execute.repository, repositoryId, ExecutionPathConfiguration.local(workingdir), userParameters)) {
			Map<String, ? extends LocalBundleStorageView> localstorages = configuredstorage.getLocalStorages();
			LocalBundleStorageView installstorage = localstorages.get(storage);
			if (installstorage == null) {
				throw new IllegalArgumentException("Local storage not found in configuration with name: " + storage
						+ " (Available: " + localstorages.keySet() + ")");
			}
			for (Entry<SakerPath, ? extends BasicFileAttributes> entry : bundles.entrySet()) {
				SakerPath bundlepath = entry.getKey();
				if (!entry.getValue().isRegularFile()) {
					SakerLog.warning().out(System.err).println("Bundle not a file, not installing: " + bundlepath);
					continue;
				}
				InstallResult installresult = installstorage.install(os -> {
					localfp.writeTo(bundlepath, ByteSink.valueOf(os));
				});
				System.out.println(
						"Installed " + bundlepath + " with bundle identifier: " + installresult.getBundleIdentifier());
			}
		}
	}

	public static Set<String> parseRemainingCommand(Iterator<String> it) {
		Set<String> result = new LinkedHashSet<>();
		while (it.hasNext()) {
			result.add(it.next());
		}
		return result;
	}

}
