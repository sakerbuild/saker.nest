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
/**
 * Contains annotation types which are interpreted by the Nest repository to provide scripting information for IDE
 * users.
 * <p>
 * The annotations in this package can be used to annotate the tasks in bundles in order to provide content assist and
 * documentational features for them.
 * <p>
 * As an entry point for this facility, developers should annotate the classes defined in the
 * {@linkplain saker.nest.bundle.BundleInformation#ENTRY_BUNDLE_TASKS bundle task declaration entry file}. Starting from
 * that, the referenced types should be annotated accordingly as well.
 * <p>
 * This facility of the Nest repository is <b>not</b> convenient, and serves as a temporary solution until a more
 * reified and complex implementation is created. It allows to declare the information directly on the classes that are
 * used with tasks, but due to the nature of annotations, it may be inconvenient to use.
 * 
 * @see NestTaskInformation
 * @see NestParameterInformation
 */
@PublicApi
package saker.nest.scriptinfo.reflection.annot;

import saker.apiextract.api.PublicApi;
