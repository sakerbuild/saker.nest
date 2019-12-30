package saker.nest.bundle.lookup;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.Collections;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;

import saker.build.runtime.repository.TaskNotFoundException;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.bundle.BundleInformation;
import saker.nest.bundle.NestRepositoryBundle;
import saker.nest.bundle.storage.AbstractBundleStorageView;
import saker.nest.bundle.storage.BundleStorageView;
import saker.nest.bundle.storage.LocalBundleStorageView;
import saker.nest.bundle.storage.ParameterBundleStorageView;
import saker.nest.bundle.storage.ServerBundleStorageView;
import saker.nest.bundle.storage.StorageViewKey;
import saker.nest.exc.BundleLoadingFailedException;

public class SingleBundleLookup extends AbstractBundleLookup {

	private final transient String storageConfigurationName;
	private final transient AbstractBundleLookup enclosingLookup;
	private final AbstractBundleStorageView storageView;

	public SingleBundleLookup(String storageConfigurationName, AbstractBundleStorageView storageView,
			AbstractBundleLookup enclosingLookup) {
		Objects.requireNonNull(storageConfigurationName, "storage configuration name");
		Objects.requireNonNull(storageView, "storage view");
		Objects.requireNonNull(enclosingLookup, "enclosing lookup");
		this.storageConfigurationName = storageConfigurationName;
		this.storageView = storageView;
		this.enclosingLookup = enclosingLookup;
	}

	public AbstractBundleLookup getEnclosingLookup() {
		return enclosingLookup;
	}

	@Override
	public LookupKey getLookupKey() {
		return new SingleLookupKeyImpl(storageView.getStorageViewKey());
	}

	@Override
	public NavigableSet<BundleIdentifier> getPresentBundlesForInformationProvider() {
		return storageView.getPresentBundlesForInformationProvider();
	}

	@Override
	public TaskLookupInfo lookupTaskBundleForInformationProvider(TaskName taskname) {
		NestRepositoryBundle taskbundle = storageView.lookupTaskBundleForInformationProvider(taskname);
		if (taskbundle == null) {
			return null;
		}
		BundleInformation bundleinfo = taskbundle.getInformation();
		String cname = bundleinfo.getTaskClassNames().get(taskname.withoutQualifiers());
		if (cname == null) {
			return null;
		}
		return new TaskLookupInfo(taskbundle, cname, enclosingLookup, storageView);
	}

	@Override
	public BundleLookup findStorageViewBundleLookup(StorageViewKey storageviewkey) {
		if (storageView.getStorageViewKey().equals(storageviewkey)) {
			return enclosingLookup;
		}
		return null;
	}

	@Override
	protected void collectPresentBundlesForInformationProvider(NavigableSet<BundleIdentifier> result) {
		NavigableSet<BundleIdentifier> bundles = storageView.getPresentBundlesForInformationProvider();
		if (!ObjectUtils.isNullOrEmpty(bundles)) {
			result.addAll(bundles);
		}
	}

	@Override
	public NavigableSet<TaskName> getPresentTaskNamesForInformationProvider() {
		return storageView.getPresentTaskNamesForInformationProvider();
	}

	@Override
	protected void collectPresentTaskNamesForInformationProvider(NavigableSet<? super TaskName> result) {
		NavigableSet<TaskName> tnames = storageView.getPresentTaskNamesForInformationProvider();
		if (!ObjectUtils.isNullOrEmpty(tnames)) {
			result.addAll(tnames);
		}
	}

	@Override
	protected void appendConfigurationUserParameters(Map<String, String> userparameters, String repositoryid) {
		storageView.appendConfigurationUserParameters(userparameters, repositoryid, storageConfigurationName);
	}

	@Override
	public Map<String, String> getLocalConfigurationUserParameters(String repositoryid) {
		return enclosingLookup.getLocalConfigurationUserParameters(repositoryid);
	}

	@Override
	protected void appendStorageConfiguration(StringBuilder sb) {
		String storagetype = storageView.getStorage().getType();
		if (!storagetype.equals(storageConfigurationName)) {
			sb.append(storageConfigurationName);
		}
		sb.append(':');
		sb.append(storagetype);
	}

	@Override
	public SimpleBundleLookupResult lookupBundle(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		NestRepositoryBundle bundle = storageView.getBundle(bundleid);
		return new SimpleBundleLookupResult(bundle, enclosingLookup, storageView);
	}

	@Override
	public SimpleBundleInformationLookupResult lookupBundleInformation(BundleIdentifier bundleid)
			throws NullPointerException, BundleLoadingFailedException {
		BundleInformation bundle = storageView.getBundleInformation(bundleid);
		return new SimpleBundleInformationLookupResult(bundle, enclosingLookup, storageView);
	}

	@Override
	public SimpleBundleVersionLookupResult lookupBundleVersions(BundleIdentifier bundleid) throws NullPointerException {
		Objects.requireNonNull(bundleid, "bundle id");
		Set<? extends BundleIdentifier> found = storageView.lookupBundleVersions(bundleid);
		if (ObjectUtils.isNullOrEmpty(found)) {
			return null;
		}
		return new SimpleBundleVersionLookupResult(found, storageView, enclosingLookup);
	}

	@Override
	public SimpleBundleIdentifierLookupResult lookupBundleIdentifiers(String bundlename)
			throws NullPointerException, IllegalArgumentException {
		Map<String, ? extends Set<? extends BundleIdentifier>> found = storageView.lookupBundleIdentifiers(bundlename);
		if (ObjectUtils.isNullOrEmpty(found)) {
			return null;
		}
		return new SimpleBundleIdentifierLookupResult(found, enclosingLookup, storageView);
	}

	@Override
	public TaskLookupInfo lookupTaskBundle(TaskName taskname) throws TaskNotFoundException {
		NestRepositoryBundle taskbundle;
		try {
			taskbundle = storageView.lookupTaskBundle(taskname);
		} catch (IOException e) {
			throw new TaskNotFoundException("Failed to look up bundle for task.", e, taskname);
		}
		BundleInformation bundleinfo = taskbundle.getInformation();
		String cname = bundleinfo.getTaskClassNames().get(taskname.withoutQualifiers());
		if (cname == null) {
			throw new TaskNotFoundException("Task not found in bundle: " + bundleinfo.getBundleIdentifier(), taskname);
		}
		return new TaskLookupInfo(taskbundle, cname, enclosingLookup, storageView);
	}

	@Override
	public Map<String, ? extends LocalBundleStorageView> getLocalStorages() {
		if (storageView instanceof LocalBundleStorageView) {
			return ImmutableUtils.singletonMap(storageConfigurationName, (LocalBundleStorageView) storageView);
		}
		return Collections.emptyMap();
	}

	@Override
	public Map<String, ? extends ParameterBundleStorageView> getParameterStorages() {
		if (storageView instanceof ParameterBundleStorageView) {
			return ImmutableUtils.singletonMap(storageConfigurationName, (ParameterBundleStorageView) storageView);
		}
		return Collections.emptyMap();
	}

	@Override
	protected void collectLocalStorages(Map<String, ? super LocalBundleStorageView> result) {
		if (storageView instanceof LocalBundleStorageView) {
			result.put(storageConfigurationName, (LocalBundleStorageView) storageView);
		}
	}

	@Override
	protected void collectParameterStorages(Map<String, ? super ParameterBundleStorageView> result) {
		if (storageView instanceof ParameterBundleStorageView) {
			result.put(storageConfigurationName, (ParameterBundleStorageView) storageView);
		}
	}

	@Override
	public Map<String, ? extends ServerBundleStorageView> getServerStorages() {
		if (storageView instanceof ServerBundleStorageView) {
			return ImmutableUtils.singletonMap(storageConfigurationName, (ServerBundleStorageView) storageView);
		}
		return Collections.emptyMap();
	}

	@Override
	protected void collectServerStorages(Map<String, ? super ServerBundleStorageView> result) {
		if (storageView instanceof ServerBundleStorageView) {
			result.put(storageConfigurationName, (ServerBundleStorageView) storageView);
		}
	}

	@Override
	public Map<String, ? extends BundleStorageView> getStorages() {
		return ImmutableUtils.singletonMap(storageConfigurationName, storageView);
	}

	@Override
	protected void collectStorages(Map<String, ? super BundleStorageView> result) {
		result.put(storageConfigurationName, storageView);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((storageView == null) ? 0 : storageView.hashCode());
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
		SingleBundleLookup other = (SingleBundleLookup) obj;
		if (storageView == null) {
			if (other.storageView != null)
				return false;
		} else if (!storageView.equals(other.storageView))
			return false;
		return true;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + "[" + storageConfigurationName + " : " + storageView + "]";
	}

	private static final class SingleLookupKeyImpl implements LookupKey, Externalizable {
		private static final long serialVersionUID = 1L;

		private StorageViewKey storageViewKey;

		/**
		 * For {@link Externalizable}.
		 */
		public SingleLookupKeyImpl() {
		}

		public SingleLookupKeyImpl(StorageViewKey storageviewkey) {
			this.storageViewKey = storageviewkey;
		}

		@Override
		public void writeExternal(ObjectOutput out) throws IOException {
			out.writeObject(storageViewKey);
		}

		@Override
		public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
			storageViewKey = (StorageViewKey) in.readObject();
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((storageViewKey == null) ? 0 : storageViewKey.hashCode());
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
			SingleLookupKeyImpl other = (SingleLookupKeyImpl) obj;
			if (storageViewKey == null) {
				if (other.storageViewKey != null)
					return false;
			} else if (!storageViewKey.equals(other.storageViewKey))
				return false;
			return true;
		}

		@Override
		public String toString() {
			return getClass().getSimpleName() + "[" + storageViewKey + "]";
		}
	}
}
