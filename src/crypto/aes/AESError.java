package crypto.aes;

public enum AESError {
	noErr,
	unknownError,
	unequalLengthError,
	keyNot128Bits,
	no32BitWord,
	NullStateError
}
