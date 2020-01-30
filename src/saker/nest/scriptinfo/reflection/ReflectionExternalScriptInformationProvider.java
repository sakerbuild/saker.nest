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
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
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
		Map<TaskName, TaskInformation> result = new TreeMap<>();
		if (tasknamekeyword == null) {
			for (TaskName tn : tasknames) {
				tn = getTaskNameWithoutVersionQualifiers(tn);
				result.putIfAbsent(tn, informationContext.getTaskInformation(tn));
			}
		} else {
			for (TaskName tn : tasknames) {
				if (tn.getName().startsWith(tasknamekeyword)) {
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

	@Override
	public Collection<? extends LiteralInformation> getLiterals(String literalkeyword, TypeInformation typecontext) {
		if (typecontext == null) {
			//can't really provide literals for unknown type
			return Collections.emptySet();
		}
		String qualifiedtypecontextname = typecontext.getTypeQualifiedName();
		if (ReflectionInformationContext.BUNDLEIDENTIFIER_CANONICAL_NAME.equals(qualifiedtypecontextname)) {
			//XXX try parse the keyword and semantically match the bundle ids
			NavigableSet<BundleIdentifier> bundles = repository.getPresentBundlesForInformationProvider();
			Collection<LiteralInformation> result = new ArrayList<>();
			if (literalkeyword == null) {
				literalkeyword = "";
			}
			for (BundleIdentifier bundleid : bundles) {
				if (!bundleid.toString().startsWith(literalkeyword)) {
					continue;
				}
				//XXX provide more information about the bundle
				result.add(informationContext.getBundleLiteralInformation(bundleid.withoutAnyQualifiers()));
				result.add(informationContext.getBundleLiteralInformation(bundleid.withoutMetaQualifiers()));
				result.add(informationContext.getBundleLiteralInformation(bundleid));
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
		String qualifiedtypecontextname = typecontext == null ? null : typecontext.getTypeQualifiedName();
		if (ReflectionInformationContext.BUNDLEIDENTIFIER_CANONICAL_NAME.equals(qualifiedtypecontextname)) {
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
