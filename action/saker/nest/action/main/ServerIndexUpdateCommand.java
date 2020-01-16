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
import java.util.LinkedHashMap;
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

public class ServerIndexUpdateCommand {
	@Parameter("-repo-id")
	public String repositoryId = NestRepositoryFactory.IDENTIFIER;

	@Parameter("-U")
	public Map<String, String> userParameters = new LinkedHashMap<>();

	@Parameter("-storage")
	@MultiParameter(String.class)
	public Set<String> storage = new TreeSet<>();

	public void call(ExecuteActionCommand execute) throws InvalidPathFormatException, IOException {
		try (ConfiguredRepositoryStorage configuredstorage = new ConfiguredRepositoryStorage(execute.repository,
				repositoryId, ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir"))),
				userParameters)) {
			Map<String, AbstractServerBundleStorageView> updatestorages = new TreeMap<>();
			Map<String, ? extends AbstractServerBundleStorageView> serverstorages = configuredstorage
					.getServerStorages();
			if (storage.isEmpty()) {
				updatestorages.putAll(serverstorages);
			} else {
				for (String s : storage) {
					AbstractServerBundleStorageView storage = serverstorages.get(s);
					if (storage != null) {
						System.out.println("Warning: No server storage found for name: " + s + " Available: "
								+ StringUtils.toStringJoin(", ", serverstorages.keySet()));
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
