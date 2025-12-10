package org.opendroidpdf;

public class PassClickResult {
	public final boolean changed;

	public PassClickResult(boolean changed) {
		this.changed = changed;
	}

	public void acceptVisitor(PassClickResultVisitor visitor) {
		// Default implementation intentionally empty.
	}
}

