package org.opendroidpdf;

public class PassClickResultChoice extends PassClickResult {
	public final String[] options;
	public final String[] selected;

	public PassClickResultChoice(boolean changed, String[] options, String[] selected) {
		super(changed);
		this.options = options;
		this.selected = selected;
	}

	@Override
	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitChoice(this);
	}
}
