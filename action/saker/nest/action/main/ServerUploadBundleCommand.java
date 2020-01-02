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

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.Iterator;
import java.util.jar.JarInputStream;

import saker.build.thirdparty.saker.util.StringUtils;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.storage.ServerBundleStorageView;
import saker.nest.bundle.storage.ServerStorageUtils;
import saker.nest.bundle.storage.ServerStorageUtils.UploadResult;
import sipka.cmdline.api.Converter;
import sipka.cmdline.api.Flag;
import sipka.cmdline.api.Parameter;
import sipka.cmdline.api.PositionalParameter;
import sipka.cmdline.runtime.ParsingIterator;

public class ServerUploadBundleCommand {
	@Parameter("-server")
	public String server = ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL;

	@Flag
	@Parameter("-overwrite")
	public boolean overwrite = false;

	@Parameter(value = "-api-key", required = true)
	@Converter(method = "parseBase64Key")
	public byte[] apiKey;
	@Parameter(value = "-api-secret", required = true)
	@Converter(method = "parseBase64Key")
	public byte[] apiSecret;

	@Parameter(value = "file", required = true)
	@PositionalParameter(-1)
	@Converter(method = "parseRemainingPathCommand")
	public Path file;

	public void call(@SuppressWarnings("unused") ExecuteActionCommand execute) throws Exception {
		BundleIdentifier bundle;
		try (InputStream is = Files.newInputStream(file);
				JarInputStream jis = new JarInputStream(is)) {
			BundleInformation bundleinfo = new BundleInformation(jis);
			bundle = bundleinfo.getBundleIdentifier();
		}
		UploadResult uploadresult = ServerStorageUtils.uploadBundle(server, bundle, os -> Files.copy(file, os), apiKey,
				apiSecret, overwrite);
		System.out.println("Uploaded bundle: " + bundle);
		System.out.println("    SHA: " + StringUtils.toHexString(uploadresult.getSHA256Hash()));
		System.out.println("    MD5: " + StringUtils.toHexString(uploadresult.getMD5Hash()));
	}

	/**
	 * @cmd-format &lt;base64&gt;
	 */
	public static byte[] parseBase64Key(ParsingIterator it) {
		String n = it.next();
		return Base64.getUrlDecoder().decode(n);
	}

	/**
	 * @cmd-format &lt;file-path&gt;
	 */
	public static Path parseRemainingPathCommand(Iterator<String> it) {
		return Paths.get(it.next());
	}
}
