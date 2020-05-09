package saker.nest.bundle;

import java.net.URI;

import saker.apiextract.api.PublicApi;

@PublicApi
public interface ExternalArchiveKey {

	public URI getUri();

	public String getEntryName();

	public static ExternalArchiveKey create(URI uri, String entryname) throws NullPointerException {
		return new SimpleExternalArchiveKey(uri, entryname);
	}

	public static ExternalArchiveKey create(URI uri) throws NullPointerException {
		return new SimpleExternalArchiveKey(uri);
	}
}
