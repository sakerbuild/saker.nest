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