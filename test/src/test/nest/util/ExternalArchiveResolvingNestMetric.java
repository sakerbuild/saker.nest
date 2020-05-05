package test.nest.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.UnsyncByteArrayInputStream;

public class ExternalArchiveResolvingNestMetric extends BasicServerNestMetric {
	private Map<URI, ByteArrayRegion> uriBytes = new TreeMap<>();

	public void put(URI uri, ByteArrayRegion bytes) {
		uriBytes.put(uri, bytes);
	}

	public void put(String uri, ByteArrayRegion bytes) {
		try {
			put(new URI(uri), bytes);
		} catch (URISyntaxException e) {
			throw new AssertionError(e);
		}
	}

	@Override
	public URL toURL(URI uri) throws MalformedURLException {
		return new URL("nesttest", null, 0,
				UUID.nameUUIDFromBytes(uri.toString().getBytes(StandardCharsets.UTF_8)).toString(),
				new URLStreamHandler() {
					@Override
					protected URLConnection openConnection(URL u) throws IOException {
						return new URLConnection(u) {
							@Override
							public void connect() throws IOException {
							}

							@Override
							public InputStream getInputStream() throws IOException {
								ByteArrayRegion bytes = uriBytes.get(uri);
								if (bytes != null) {
									return new UnsyncByteArrayInputStream(bytes);
								}
								throw new FileNotFoundException(uri.toString());
							}
						};
					}
				});
	}

}
