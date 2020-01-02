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

public class NonSpaceIterator {
	private CharSequence cs;
	private int idx;

	public NonSpaceIterator(CharSequence cs) {
		this.cs = cs;
		moveToNext();
	}

	private void moveToNext() {
		while (idx < cs.length()) {
			char c = cs.charAt(idx);
			if (c == ' ' || c == '\t') {
				++idx;
			} else {
				break;
			}
		}
	}

	public int getIndex() {
		return idx;
	}

	public CharSequence getCharSequence() {
		return cs;
	}

	public boolean hasNext() {
		return idx < cs.length();
	}

	public char peek() {
		return cs.charAt(idx);
	}

	public char next() {
		char res = cs.charAt(idx);
		move();
		return res;
	}

	public void move() {
		++idx;
		moveToNext();
	}
}