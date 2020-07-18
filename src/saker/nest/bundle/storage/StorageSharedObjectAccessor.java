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
package saker.nest.bundle.storage;

public interface StorageSharedObjectAccessor {
	public static final StorageSharedObjectAccessor NULL_ACCESSOR = new StorageSharedObjectAccessor() {
		@Override
		public void setSharedObject(Object key, Object value) {
		}

		@Override
		public Object getSharedObject(Object key) {
			return null;
		}
	};

	//@since saker.build 0.8.15
	public void setSharedObject(Object key, Object value);

	//@since saker.build 0.8.15
	public Object getSharedObject(Object key);
}
