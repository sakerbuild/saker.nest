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
package testing.saker.nest;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.Map;

import saker.build.file.path.SakerPath;
import saker.build.file.provider.FileHashResult;
import saker.build.file.provider.SakerFileProvider;
import saker.build.runtime.params.ExecutionPathConfiguration;

public interface NestMetric {
	public default Integer getServerRequestResponseCode(String requesturl) throws IOException {
		return null;
	}

	public default InputStream getServerRequestResponseStream(String requesturl) throws IOException {
		throw new IOException("no data (" + this + "): " + requesturl);
	}

	public default InputStream getServerRequestResponseErrorStream(String requesturl) throws IOException {
		throw new IOException("no data (" + this + "): " + requesturl);
	}

	public default Map<String, String> getServerRequestResponseHeaders(String requesturl) {
		return Collections.emptyMap();
	}

	public default PublicKey overrideServerBundleSignaturePublicKey(String server, int version) {
		return null;
	}

	public default boolean allowCachedVerificationState(String bundleid) {
		return true;
	}

	public default FileHashResult overrideParameterBundlePerceivedHash(SakerFileProvider fp, SakerPath bundlepath,
			String algorithm) throws NoSuchAlgorithmException, IOException {
		return null;
	}

	public default boolean offerConfiguredRepositoryCache(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters, Object configuredstorage) {
		return false;
	}

	public default Object retrieveCachedConfiguredRepository(Object repositoryobj, String repoid,
			ExecutionPathConfiguration pathconfig, Map<String, String> userparameters) {
		return null;
	}

	public default Boolean overrideServerUncacheRequestsValue() {
		return false;
	}

	public default Path overrideNativeLibraryPath(ClassLoader cl, Path libpath) {
		return libpath;
	}

	public default URL toURL(URI uri) throws MalformedURLException {
		return uri.toURL();
	}
}
