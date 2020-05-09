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
package saker.nest.bundle.storage;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.security.MessageDigest;
import java.util.Map;
import java.util.NavigableSet;

import saker.build.runtime.params.ExecutionPathConfiguration;
import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.nest.bundle.AbstractNestRepositoryBundle;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.ExternalArchive;
import saker.nest.bundle.ExternalArchiveKey;
import saker.nest.bundle.ExternalDependencyInformation;
import saker.nest.bundle.Hashes;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.exc.BundleLoadingFailedException;
import saker.nest.exc.ExternalArchiveLoadingFailedException;
import testing.saker.nest.TestFlag;

public abstract class AbstractBundleStorageView implements BundleStorageView {
	public abstract Object detectChanges(ExecutionPathConfiguration pathconfig);

	public abstract void handleChanges(ExecutionPathConfiguration pathconfig, Object detectedchanges);

	public abstract NestRepositoryBundle lookupTaskBundle(TaskName taskname)
			throws NullPointerException, TaskNotFoundException, IOException;

	public abstract void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid,
			String storagename);

	@Override
	public abstract AbstractNestRepositoryBundle getBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	@Override
	public abstract AbstractBundleStorage getStorage();

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + getStorage() + "]";
	}

	public abstract NavigableSet<TaskName> getPresentTaskNamesForInformationProvider();

	public abstract NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider();

	public abstract void updateStorageViewHash(MessageDigest digest);

	public NestRepositoryBundle lookupTaskBundleForInformationProvider(TaskName taskname) {
		try {
			return lookupTaskBundle(taskname);
		} catch (IOException | TaskNotFoundException e) {
			return null;
		}
	}

	public InputStream openExternalDependencyURI(URI uri, Hashes expectedhashes) throws IOException {
		URL url;
		if (TestFlag.ENABLED) {
			url = TestFlag.metric().toURL(uri);
		} else {
			url = uri.toURL();
		}
		URLConnection conn = url.openConnection();
		if (conn instanceof HttpURLConnection) {
			HttpURLConnection httpconn = (HttpURLConnection) conn;
			httpconn.setRequestMethod("GET");
			httpconn.setDoOutput(false);
			httpconn.setDoInput(true);
			httpconn.setConnectTimeout(10000);
			httpconn.setReadTimeout(10000);
		}
		return conn.getInputStream();
	}

	@Override
	public abstract Map<? extends ExternalArchiveKey, ? extends ExternalArchive> loadExternalArchives(
			ExternalDependencyInformation depinfo)
			throws NullPointerException, IllegalArgumentException, ExternalArchiveLoadingFailedException;
}
