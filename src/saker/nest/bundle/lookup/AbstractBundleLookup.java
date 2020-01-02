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
package saker.nest.bundle.lookup;

import java.util.Map;
import java.util.NavigableSet;

import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.ParameterBundleStorageView;
import saker.nest.bundle.storage.ServerBundleStorageView;
import saker.nest.bundle.storage.StorageViewKey;
import saker.nest.exc.BundleLoadingFailedException;

public abstract class AbstractBundleLookup implements BundleLookup {
	public abstract TaskLookupInfo lookupTaskBundle(TaskName taskname) throws TaskNotFoundException;

	@Override
	public abstract SimpleBundleLookupResult lookupBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	@Override
	public abstract SimpleBundleInformationLookupResult lookupBundleInformation(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException;

	@Override
	public abstract SimpleBundleVersionLookupResult lookupBundleVersions(BundleIdentifier bundleid)
			throws NullPointerException;

	@Override
	public abstract SimpleBundleIdentifierLookupResult lookupBundleIdentifiers(String bundlename)
			throws NullPointerException, IllegalArgumentException;

	public abstract Map<String, ? extends LocalBundleStorageView> getLocalStorages();

	public abstract Map<String, ? extends ParameterBundleStorageView> getParameterStorages();

	public abstract Map<String, ? extends ServerBundleStorageView> getServerStorages();

	public abstract Map<String, ? extends BundleStorageView> getStorages();

	public abstract NavigableSet<TaskName> getPresentTaskNamesForInformationProvider();

	public abstract NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider();

	public abstract TaskLookupInfo lookupTaskBundleForInformationProvider(TaskName tn);

	public abstract BundleLookup findStorageViewBundleLookup(StorageViewKey storageviewkey);
	
	protected abstract void appendStorageConfiguration(StringBuilder sb);

	protected abstract void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid);

	protected abstract void collectLocalStorages(Map<String, ? super LocalBundleStorageView> result);

	protected abstract void collectParameterStorages(Map<String, ? super ParameterBundleStorageView> result);

	protected abstract void collectServerStorages(Map<String, ? super ServerBundleStorageView> result);

	protected abstract void collectStorages(Map<String, ? super BundleStorageView> result);

	protected abstract void collectPresentTaskNamesForInformationProvider(NavigableSet<? super TaskName> result);

	protected abstract void collectPresentBundlesForInformationProvider(NavigableSet<BundleIdentifier> result);

}
