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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.io.IOUtils;
import saker.build.thirdparty.saker.util.io.SerialUtils;
import saker.nest.ConfiguredRepositoryStorage;
import saker.nest.NestRepositoryFactory;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.ParameterBundleStorageView;
import saker.nest.bundle.storage.ServerBundleStorageView;
import saker.nest.bundle.storage.StorageViewKey;
import saker.nest.exc.BundleLoadingFailedException;

public class MultiBundleLookup extends AbstractBundleLookup {

	private final List<? extends AbstractBundleLookup> lookups;

	public MultiBundleLookup(List<? extends AbstractBundleLookup> lookups) {
		Objects.requireNonNull(lookups, "lookups");
		this.lookups = lookups;
	}

	@Override
	public LookupKey getLookupKey() {
		LookupKey[] keys = new LookupKey[lookups.size()];
		for (int i = 0; i < keys.length; i++) {
			keys[i] = lookups.get(i).getLookupKey();
		}
		return new MultiLookupKeyImpl(ImmutableUtils.makeImmutableList(keys));
	}

	@Override
	public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
		NavigableSet<BundleIdentifier> result = new TreeSet<>();
		collectPresentBundlesForInformationProvider(result);
		return result;
	}

	@Override
	public TaskLookupInfo lookupTaskBundleForInformationProvider(TaskName tn) {
		for (AbstractBundleLookup lookup : lookups) {
			TaskLookupInfo found = lookup.lookupTaskBundleForInformationProvider(tn);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	@Override
	public BundleLookup findStorageViewBundleLookup(StorageViewKey storageviewkey) {
		for (AbstractBundleLookup lookup : lookups) {
			BundleLookup found = lookup.findStorageViewBundleLookup(storageviewkey);
			if (found != null) {
				return found;
			}
		}
		return null;
	}

	@Override
	protected void collectPresentBundlesForInformationProvider(NavigableSet<BundleIdentifier> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectPresentBundlesForInformationProvider(result);
		}
	}

	@Override
	public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
		NavigableSet<TaskName> result = new TreeSet<>();
		collectPresentTaskNamesForInformationProvider(result);
		return result;
	}

	@Override
	protected void collectPresentTaskNamesForInformationProvider(NavigableSet<? super TaskName> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectPresentTaskNamesForInformationProvider(result);
		}
	}

	@Override
	public Map<String, String> getLocalConfigurationUserParameters(String repositoryid) {
		if (repositoryid == null) {
			repositoryid = NestRepositoryFactory.IDENTIFIER;
		}
		TreeMap<String, String> result = new TreeMap<>();
		result.put(repositoryid + "." + ConfiguredRepositoryStorage.PARAMETER_NEST_REPOSITORY_STORAGE_CONFIGURATION,
				toStorageConfigurationString());
		appendConfigurationUserParameters(result, repositoryid);
		return result;
	}

	@Override
	protected void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.appendConfigurationUserParameters(userparameters, repositoryid);
		}
	}

	@Override
	protected void appendStorageConfiguration(StringBuilder sb) {
		sb.append('[');
		for (Iterator<? extends AbstractBundleLookup> it = lookups.iterator(); it.hasNext();) {
			AbstractBundleLookup lookup = it.next();
			lookup.appendStorageConfiguration(sb);
			if (it.hasNext()) {
				sb.append(',');
			}
		}
		sb.append(']');
	}

	@Override
	public SimpleBundleLookupResult lookupBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		BundleLoadingFailedException exc = null;
		for (AbstractBundleLookup lookup : lookups) {
			try {
				SimpleBundleLookupResult result = lookup.lookupBundle(bundleid);
				return result;
			} catch (BundleLoadingFailedException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
		throw new BundleLoadingFailedException("Bundle not found: " + bundleid);
	}

	@Override
	public SimpleBundleInformationLookupResult lookupBundleInformation(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		BundleLoadingFailedException exc = null;
		for (AbstractBundleLookup lookup : lookups) {
			try {
				SimpleBundleInformationLookupResult result = lookup.lookupBundleInformation(bundleid);
				return result;
			} catch (BundleLoadingFailedException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
		throw new BundleLoadingFailedException("Bundle not found: " + bundleid);
	}

	@Override
	public SimpleBundleVersionLookupResult lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException {
		Objects.requireNonNull(bundleid, "bundle id");
		for (AbstractBundleLookup lookup : lookups) {
			SimpleBundleVersionLookupResult result = lookup.lookupBundleVersions(bundleid);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public SimpleBundleIdentifierLookupResult lookupBundleIdentifiers(String bundlename)
			throws NullPointerException, IllegalArgumentException {
		for (AbstractBundleLookup lookup : lookups) {
			SimpleBundleIdentifierLookupResult result = lookup.lookupBundleIdentifiers(bundlename);
			if (result != null) {
				return result;
			}
		}
		return null;
	}

	@Override
	public TaskLookupInfo lookupTaskBundle(TaskName taskname) throws TaskNotFoundException {
		TaskNotFoundException exc = null;
		for (ListIterator<? extends AbstractBundleLookup> it = lookups.listIterator(); it.hasNext();) {
			AbstractBundleLookup lookup = it.next();
			try {
				TaskLookupInfo result = lookup.lookupTaskBundle(taskname);
				return result;
			} catch (TaskNotFoundException e) {
				exc = IOUtils.addExc(exc, e);
			}
		}
		IOUtils.throwExc(exc);
		throw new TaskNotFoundException(taskname);
	}

	@Override
	public Map<String, ? extends LocalBundleStorageView> getLocalStorages() {
		Map<String, LocalBundleStorageView> result = new TreeMap<>();
		collectLocalStorages(result);
		return result;
	}

	@Override
	protected void collectLocalStorages(Map<String, ? super LocalBundleStorageView> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectLocalStorages(result);
		}
	}

	@Override
	public Map<String, ? extends ParameterBundleStorageView> getParameterStorages() {
		Map<String, ParameterBundleStorageView> result = new TreeMap<>();
		collectParameterStorages(result);
		return result;
	}

	@Override
	protected void collectParameterStorages(Map<String, ? super ParameterBundleStorageView> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectParameterStorages(result);
		}
	}

	@Override
	public Map<String, ? extends ServerBundleStorageView> getServerStorages() {
		Map<String, ServerBundleStorageView> result = new TreeMap<>();
		collectServerStorages(result);
		return result;
	}

	@Override
	protected void collectServerStorages(Map<String, ? super ServerBundleStorageView> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectServerStorages(result);
		}
	}

	@Override
	public Map<String, ? extends BundleStorageView> getStorages() {
		Map<String, BundleStorageView> result = new TreeMap<>();
		collectStorages(result);
		return result;
	}

	@Override
	protected void collectStorages(Map<String, ? super BundleStorageView> result) {
		for (AbstractBundleLookup lookup : lookups) {
			lookup.collectStorages(result);
		}
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((lookups == null) ? 0 : lookups.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MultiBundleLookup other = (MultiBundleLookup) obj;
		if (lookups == null) {
			if (other.lookups != null)
				return false;
		} else if (!lookups.equals(other.lookups))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + lookups + "]";
	}

	private String toStorageConfigurationString() {
		StringBuilder storageconfigbuilder = new StringBuilder();
		appendStorageConfiguration(storageconfigbuilder);
		return storageconfigbuilder.toString();
	}

	private static final class MultiLookupKeyImpl implements LookupKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private List<LookupKey> keys;

		/**
		 * For {@link Externalizable}.
		 */
		public MultiLookupKeyImpl() {
		}

		public MultiLookupKeyImpl(List<LookupKey> keys) {
			this.keys = keys;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			SerialUtils.writeExternalCollection(out, keys);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			keys = SerialUtils.readExternalImmutableList(in);
		}

		@Override
		public int hashCode() {
			return keys.hashCode();
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			MultiLookupKeyImpl other = (MultiLookupKeyImpl) obj;
			if (keys == null) {
				if (other.keys != null)
					return false;
			} else if (!keys.equals(other.keys))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + keys;
		}

	}
}
