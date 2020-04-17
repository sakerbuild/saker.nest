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
package saker.nest.scriptinfo.reflection;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import saker.build.scripting.model.FormattedTextContent;
import saker.build.scripting.model.MultiFormattedTextContent;
import saker.build.scripting.model.info.ExternalScriptInformationProvider;
import saker.build.scripting.model.info.LiteralInformation;
import saker.build.scripting.model.info.TaskInformation;
import saker.build.scripting.model.info.TaskParameterInformation;
import saker.build.scripting.model.info.TypeInformation;
import saker.build.task.TaskName;
import saker.build.thirdparty.saker.util.ImmutableUtils;
import saker.build.thirdparty.saker.util.ObjectUtils;
import saker.nest.NestBuildRepositoryImpl;
import saker.nest.bundle.BundleIdentifier;
import saker.nest.scriptinfo.reflection.annot.NestInformation;

public class ReflectionExternalScriptInformationProvider implements ExternalScriptInformationProvider {
	private static final BundleIdentifier[] EMPTY_BUNDLEIDENTIFIER_ARRAY = new BundleIdentifier[0];

	private final NestBuildRepositoryImpl repository;
	private final ReflectionInformationContext informationContext;

	public ReflectionExternalScriptInformationProvider(NestBuildRepositoryImpl buildrepository) {
		this.repository = buildrepository;
		this.informationContext = new ReflectionInformationContext(buildrepository);
	}

	private static TaskName getTaskNameWithoutVersionQualifiers(TaskName tn) {
		NavigableSet<String> qualifiers = tn.getTaskQualifiers();
		if (!BundleIdentifier.hasVersionQualifier(qualifiers)) {
			return tn;
		}
		qualifiers = new TreeSet<>(qualifiers);
		for (Iterator<String> it = qualifiers.iterator(); it.hasNext();) {
			String q = it.next();
			if (BundleIdentifier.isValidVersionQualifier(q)) {
				it.remove();
			}
		}
		return TaskName.valueOf(tn.getName(), qualifiers);
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTasks(String tasknamekeyword) {
		NavigableSet<TaskName> tasknames = repository.getPresentTaskNamesForInformationProvider();
		Map<TaskName, TaskInformation> result = new LinkedHashMap<>();
		if (!ObjectUtils.isNullOrEmpty(tasknames)) {
			if (tasknamekeyword == null) {
				for (TaskName tn : tasknames) {
					tn = getTaskNameWithoutVersionQualifiers(tn);
					result.putIfAbsent(tn, informationContext.getTaskInformation(tn));
				}
			} else {
				//add suggestions for tasks that don't start with, but contain the keyword
				//but add them after the start with ones
				List<TaskName> containsnames = new ArrayList<>();
				for (TaskName tn : tasknames) {
					String tasknamestr = tn.getName();
					if (tasknamestr.startsWith(tasknamekeyword)) {
						tn = getTaskNameWithoutVersionQualifiers(tn);
						result.putIfAbsent(tn, informationContext.getTaskInformation(tn));
					} else if (tasknamestr.contains(tasknamekeyword)) {
						containsnames.add(tn);
					}
				}
				for (TaskName tn : containsnames) {
					tn = getTaskNameWithoutVersionQualifiers(tn);
					result.putIfAbsent(tn, informationContext.getTaskInformation(tn));
				}
			}
		}
		return result;
	}

	@Override
	public Map<TaskName, ? extends TaskInformation> getTaskInformation(TaskName taskname) {
		return ImmutableUtils.singletonNavigableMap(taskname, informationContext.getTaskInformation(taskname));
	}

	@Override
	public Map<TaskName, ? extends TaskParameterInformation> getTaskParameterInformation(TaskName taskname,
			String parametername) {
		// default implementation is fine
		return ExternalScriptInformationProvider.super.getTaskParameterInformation(taskname, parametername);
	}

	private static boolean isBundleIdentifierTypeContext(TypeInformation typecontext,
			Set<TypeInformation> checkedtypes) {
		if (typecontext == null || !checkedtypes.add(typecontext)) {
			return false;
		}

		if (ReflectionInformationContext.BUNDLEIDENTIFIER_CANONICAL_NAME.equals(typecontext.getTypeQualifiedName())) {
			return true;
		}
		Set<TypeInformation> relatedtypes = typecontext.getRelatedTypes();
		if (!ObjectUtils.isNullOrEmpty(relatedtypes)) {
			for (TypeInformation rtype : relatedtypes) {
				if (isBundleIdentifierTypeContext(rtype)) {
					return true;
				}
			}
		}
		Set<TypeInformation> supertypes = typecontext.getSuperTypes();
		if (!ObjectUtils.isNullOrEmpty(supertypes)) {
			for (TypeInformation stype : supertypes) {
				if (isBundleIdentifierTypeContext(stype)) {
					return true;
				}
			}
		}
		return false;
	}

	private static boolean isBundleIdentifierTypeContext(TypeInformation typecontext) {
		if (typecontext == null) {
			return false;
		}
		return isBundleIdentifierTypeContext(typecontext, new HashSet<>());
	}

	@Override
	public Collection<? extends LiteralInformation> getLiterals(String literalkeyword, TypeInformation typecontext) {
		if (isBundleIdentifierTypeContext(typecontext)) {
			//XXX try parse the keyword and semantically match the bundle ids
			NavigableSet<BundleIdentifier> bundles = repository.getPresentBundlesForInformationProvider();
			Collection<LiteralInformation> result = new LinkedHashSet<>();
			if (literalkeyword == null) {
				literalkeyword = "";
			}
			if (!ObjectUtils.isNullOrEmpty(bundles)) {
				//XXX provide more information about each bundle?

				//add suggestions for bundles that don't start with, but contain the keyword
				//but add them after the start with ones
				LinkedHashSet<BundleIdentifier> bundlestoadd = new LinkedHashSet<>();
				List<BundleIdentifier> containsbundleids = new ArrayList<>();
				for (BundleIdentifier bundleid : bundles) {
					String bundleidstr = bundleid.toString();
					if (!bundleidstr.startsWith(literalkeyword)) {
						if (bundleidstr.contains(literalkeyword)) {
							containsbundleids.add(bundleid);
						}
						continue;
					}
					bundlestoadd.add(bundleid.withoutAnyQualifiers());
					bundlestoadd.add(bundleid.withoutMetaQualifiers());
					bundlestoadd.add(bundleid);
				}
				for (BundleIdentifier bundleid : containsbundleids) {
					bundlestoadd.add(bundleid.withoutAnyQualifiers());
					bundlestoadd.add(bundleid.withoutMetaQualifiers());
					bundlestoadd.add(bundleid);
				}
				BundleIdentifier[] bundlestoaddarray = bundlestoadd.toArray(EMPTY_BUNDLEIDENTIFIER_ARRAY);
				Arrays.sort(bundlestoaddarray, (l, r) -> {
					//order:
					//first the ones that don't have ANY qualifiers
					//then the ones that don't have meta qualifiers
					//then the meta qualified ones
					if (!l.hasAnyQualifiers()) {
						if (!r.hasAnyQualifiers()) {
							return 0;
						}
						return -1;
					}
					if (!r.hasAnyQualifiers()) {
						return 1;
					}
					//both has qualifiers
					if (!l.hasMetaQualifiers()) {
						if (!r.hasMetaQualifiers()) {
							return 0;
						}
						return -1;
					}
					if (!r.hasMetaQualifiers()) {
						return 1;
					}
					//both have meta qualifiers
					return 0;
				});

				for (BundleIdentifier bundleid : bundlestoaddarray) {
					if (bundleid.toString().contains(literalkeyword)) {
						result.add(informationContext.getBundleLiteralInformation(bundleid));
					}
				}
			}
			return result;
		}
		return ExternalScriptInformationProvider.super.getLiterals(literalkeyword, typecontext);
	}

	@Override
	public LiteralInformation getLiteralInformation(String literal, TypeInformation typecontext) {
		if (literal == null) {
			return null;
		}
		if (isBundleIdentifierTypeContext(typecontext)) {
			try {
				BundleIdentifier bundleid = BundleIdentifier.valueOf(literal);
				//XXX more info about the found bundle?
				return informationContext.getBundleLiteralInformation(bundleid);
			} catch (IllegalArgumentException e) {
			}
		}
		return ExternalScriptInformationProvider.super.getLiteralInformation(literal, typecontext);
	}

	public static FormattedTextContent toFormattedTextContentFilter(String name, NestInformation... infos) {
		if (infos.length == 0) {
			return null;
		}
		Map<String, String> formats = new TreeMap<>();
		for (NestInformation i : infos) {
			String iname = i.value();
			if (Objects.equals(iname, name)) {
				formats.put(i.format(), iname);
			}
		}
		return MultiFormattedTextContent.create(formats);
	}

	public static FormattedTextContent toFormattedTextContent(NestInformation... infos) {
		if (ObjectUtils.isNullOrEmpty(infos)) {
			return null;
		}
		Map<String, String> formats = new TreeMap<>();
		for (NestInformation i : infos) {
			formats.put(i.format(), i.value());
		}
		return MultiFormattedTextContent.create(formats);
	}

}
