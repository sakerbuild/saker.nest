package saker.nest.action.main;

import java.io.IOException;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Set;

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

public class LocalInstallBundleCommand {
	@Parameter("-repo-id")
	public String repositoryId = NestRepositoryFactory.IDENTIFIER;

	@Parameter("-U")
	public Map<String, String> userParameters = new LinkedHashMap<>();

	@Parameter("-storage")
	public String storage = ConfiguredRepositoryStorage.STORAGE_TYPE_LOCAL;

	@Parameter
	@PositionalParameter(value = -1)
	@Converter(method = "parseRemainingCommand")
	public Set<String> bundles = new LinkedHashSet<>();

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
		try (ConfiguredRepositoryStorage configuredstorage = new ConfiguredRepositoryStorage(execute.repository,
				repositoryId, ExecutionPathConfiguration.local(SakerPath.valueOf(System.getProperty("user.dir"))),
				userParameters)) {
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
