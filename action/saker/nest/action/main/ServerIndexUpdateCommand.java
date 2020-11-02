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
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.exception.InvalidPathFormatException;
import saker.build.file.path.SakerPath;
import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.thirdparty.saker.util.StringUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils;
import saker.build.thirdparty.saker.util.thread.ThreadUtils.ThreadWorkPool;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.storage.AbstractServerBundleStorageView;
import sipka.cmdline.api.MultiParameter;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.runtime.InvalidArgumentValueException;

/**
 * <pre>
 * Updates the index files for the server bundle storage.
 * 
 * The command will freshen up the index files and retrieve
 * the latest ones from the associated servers.
 * 
 * Invoking this command can be useful if you've just published
 * a package and want to see its results as soon as possible.
 * Without the manual update of the index files, seeing the published
 * package may take some time.
 * </pre>
 */
public class ServerIndexUpdateCommand {
	private static final String PARAM_NAME_U = "-U";
	private static final String PARAM_NAME_STORAGE = "-storage";

	/**
	 * <pre>
	 * Specifies the names of the configured server bundle storages
	 * of which the index files should be updated.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_STORAGE)
	@MultiParameter(String.class)
	public Set<String> storage = new TreeSet<>();

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

	private Map<String, String> userParameters = new TreeMap<>();

	/**
	 * <pre>
	 * Specifies the user parameters for configuring the repository.
	 * 
	 * This string key-value pairs are interpreted the same way as the
	 * -U user parameters for the build execution.
	 * </pre>
	 */
	@Parameter(PARAM_NAME_U)
	public void userParameter(String key, String value) {
		if (userParameters.containsKey(key)) {
			throw new InvalidArgumentValueException("User parameter specified multiple times: " + key, PARAM_NAME_U);
		}
		userParameters.put(key, value);
	}

	public void call(ExecuteActionCommand execute) throws InvalidPathFormatException, IOException {
		boolean displayedavailable = false;
		try (ConfiguredRepositoryStorage configuredstorage = ConfiguredRepositoryStorage.forRepositoryAction(
				execute.repository, repositoryId,
				ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir"))), userParameters)) {
			Map<String, AbstractServerBundleStorageView> updatestorages = new TreeMap<>();
			Map<String, ? extends AbstractServerBundleStorageView> serverstorages = configuredstorage
					.getServerStorages();
			if (storage.isEmpty()) {
				updatestorages.putAll(serverstorages);
			} else {
				for (String s : storage) {
					AbstractServerBundleStorageView storage = serverstorages.get(s);
					if (storage != null) {
						System.out.println("Warning: No server storage found for name: " + s);
						if (!displayedavailable) {
							displayedavailable = true;
							System.out.println(
									"    Available: " + StringUtils.toStringJoin(", ", serverstorages.keySet()));
						}
					} else {
						updatestorages.put(s, storage);
					}
				}
			}
			if (updatestorages.isEmpty()) {
				System.out.println("Warning: No server storages configured. No indexes are updated.");
				return;
			}
			try (ThreadWorkPool wp = ThreadUtils.newDynamicWorkPool()) {
				for (Entry<String, AbstractServerBundleStorageView> entry : updatestorages.entrySet()) {
					wp.offer(() -> performBundleIndexUpdate(entry));
					wp.offer(() -> performTaskIndexUpdate(entry));
				}
			}
		}
	}

	private static void performBundleIndexUpdate(Entry<String, AbstractServerBundleStorageView> entry)
			throws IOException {
		String storagename = entry.getKey();
		System.out.println("Updating " + storagename + " bundle indexes...");
		entry.getValue().updateBundleIndexFiles();
		System.out.println("Bundle index update done. (" + storagename + ")");
	}

	private static void performTaskIndexUpdate(Entry<String, AbstractServerBundleStorageView> entry)
			throws IOException {
		String storagename = entry.getKey();
		System.out.println("Updating " + storagename + " task indexes...");
		entry.getValue().updateTaskIndexFiles();
		System.out.println("Task index update done. (" + storagename + ")");
	}
}
