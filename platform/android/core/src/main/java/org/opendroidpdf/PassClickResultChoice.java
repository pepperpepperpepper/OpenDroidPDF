package org.opendroidpdf;

public class PassClickResultChoice extends PassClickResult {
	public final String[] options;
	public final String[] selected;
	public final boolean multiSelect;
	public final boolean editable;

	public PassClickResultChoice(boolean changed, String[] options, String[] selected, boolean multiSelect, boolean editable) {
		super(changed);
		this.options = options;
		this.selected = selected;
		this.multiSelect = multiSelect;
		this.editable = editable;
	}

	@Override
	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitChoice(this);
	}
}
