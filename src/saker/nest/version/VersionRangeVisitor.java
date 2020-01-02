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
package saker.nest.version;

/**
 * Visitor interface for visiting {@link VersionRange} instances.
 * 
 * @param <R>
 *            The result type of the visitor.
 * @param <P>
 *            The parameter type of the visitor.
 * @see VersionRange#accept(VersionRangeVisitor, Object)
 */
public interface VersionRangeVisitor<R, P> {

	/**
	 * Visits an intersection version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(IntersectionVersionRange range, P param);

	/**
	 * Visits an intersection version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(BoundedVersionRange range, P param);

	/**
	 * Visits an exact version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(ExactVersionRange range, P param);

	/**
	 * Visits an maximum version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(MaximumVersionRange range, P param);

	/**
	 * Visits an minimum version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(MinimumVersionRange range, P param);

	/**
	 * Visits an union version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(UnionVersionRange range, P param);

	/**
	 * Visits an base version version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(BaseVersionVersionRange range, P param);

	/**
	 * Visits an unsatisfiable version range.
	 * 
	 * @param range
	 *            The version range.
	 * @param param
	 *            The parameter passed to {@link VersionRange#accept(VersionRangeVisitor, Object)}.
	 * @return The result of the visiting.
	 */
	public R visit(UnsatisfiableVersionRange range, P param);

}
