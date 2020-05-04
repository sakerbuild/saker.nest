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
package saker.nest.bundle;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.NavigableSet;

import saker.apiextract.api.PublicApi;
import saker.build.thirdparty.saker.util.io.ByteArrayRegion;
import saker.build.thirdparty.saker.util.io.StreamUtils;

@PublicApi
public interface ExternalArchive {
	public URI getOrigin();

	public NavigableSet<String> getEntryNames();

	public InputStream openEntry(String name) throws NullPointerException, IOException;

	public default boolean hasEntry(String name) throws NullPointerException {
		return name != null && getEntryNames().contains(name);
	}

	public default ByteArrayRegion getEntryBytes(String name) throws NullPointerException, IOException {
		try (InputStream is = openEntry(name)) {
			return StreamUtils.readStreamFully(is);
		}
	}
}
