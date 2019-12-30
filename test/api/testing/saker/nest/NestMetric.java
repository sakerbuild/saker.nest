package testing.saker.nest;

import java.io.IOException;
import java.io.InputStream;
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
}
