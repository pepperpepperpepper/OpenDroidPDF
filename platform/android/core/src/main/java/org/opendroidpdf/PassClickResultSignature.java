package org.opendroidpdf;

public class PassClickResultSignature extends PassClickResult {
	public final SignatureState state;

	public PassClickResultSignature(boolean changed, int stateIndex) {
		super(changed);
		this.state = SignatureState.values()[stateIndex];
	}

	@Override
	public void acceptVisitor(PassClickResultVisitor visitor) {
		visitor.visitSignature(this);
	}
}

