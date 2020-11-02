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
import java.nio.file.InvalidPathException;
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
import sipka.cmdline.runtime.InvalidArgumentFormatException;
import sipka.cmdline.runtime.ParseUtil;
import sipka.cmdline.runtime.ParsingIterator;

/**
 * <pre>
 * Uploads a single bundle to the specified saker.nest repository.
 * </pre>
 */
public class ServerUploadBundleCommand {
	/**
	 * <pre>
	 * The URL of the server to which the upload should be performed.
	 * 
	 * It is https://api.nest.saker.build by default.
	 * </pre>
	 */
	@Parameter("-server")
	public String server = ServerBundleStorageView.REPOSITORY_DEFAULT_SERVER_URL;

	/**
	 * <pre>
	 * Flag specifying that the already existing bundles can be overwritten.
	 * 
	 * If not set, the server will decide whether or not the bundles may be
	 * overwritten.
	 * </pre>
	 */
	@Flag
	@Parameter("-overwrite")
	public Boolean overwrite = null;

	/**
	 * <pre>
	 * Specifies the API Key to be used for the upload request.
	 * 
	 * The argument is expected to be in URL safe Base64 format.
	 * </pre>
	 */
	@Parameter(value = "-api-key", required = true)
	@Converter(method = "parseBase64Key")
	public byte[] apiKey;
	/**
	 * <pre>
	 * Specifies the API Secret to be used for the upload request.
	 * 
	 * The argument is expected to be in URL safe Base64 format.
	 * </pre>
	 */
	@Parameter(value = "-api-secret", required = true)
	@Converter(method = "parseBase64Key")
	public byte[] apiSecret;

	/**
	 * <pre>
	 * Path to the bundle to upload.
	 * 
	 * The specified Java archive should be a valid saker.nest bundle.
	 * If not, an exception is thrown before the upload request is initiated.
	 * </pre>
	 */
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
	public static byte[] parseBase64Key(String parametername, ParsingIterator it) {
		String n = ParseUtil.requireNextArgument(parametername, it);
		return Base64.getUrlDecoder().decode(n);
	}

	/**
	 * @cmd-format &lt;file-path&gt;
	 */
	public static Path parseRemainingPathCommand(String parametername, Iterator<String> it) {
		String pathstr = ParseUtil.requireNextArgument(parametername, it);
		try {
			return Paths.get(pathstr);
		} catch (InvalidPathException e) {
			throw new InvalidArgumentFormatException("Invalid local path format for: " + pathstr, e, parametername);
		}
	}
}
