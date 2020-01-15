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
package saker.nest.utils;

public class IdentityComparisonPair<T> {
	public final T left;
	public final T right;

	public IdentityComparisonPair(T left, T right) {
		this.left = left;
		this.right = right;
	}

	@Override
	public int hashCode() {
		return System.identityHashCode(left) ^ System.identityHashCode(right);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		IdentityComparisonPair<?> other = (IdentityComparisonPair<?>) obj;
		if (left != other.left) {
			return false;
		}
		if (right != other.right) {
			return false;
		}
		return true;
	}

}
