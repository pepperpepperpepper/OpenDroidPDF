package org.opendroidpdf;

/**
 * Keeps widget signature state in sync with the native enum defined in widgets_signature.c.
 */
enum SignatureState {
	NoSupport,
	Unsigned,
	Signed
}
