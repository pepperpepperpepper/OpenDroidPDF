package org.opendroidpdf;

public class PassClickResultText extends PassClickResult {
	public final String text;

	public PassClickResultText(boolean changed, String text) {
		super(changed);
		this.text = text;
	}

	@Override
	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitText(this);
	}
}

