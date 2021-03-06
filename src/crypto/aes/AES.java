package crypto.aes;

import java.util.ArrayList;

/**
 * Official Documentation: http://nvlpubs.nist.gov/nistpubs/FIPS/NIST.FIPS.197.pdf
 * @author lukasbischof
 *
 */

/*
 *
 * 1 Word = 1 short = 2 Byte = 16 Bit
 *
 */

/**
 * Die Implementierung des AES-Algorithmus
 * @author Lukas
 *
 */
public class AES {
    /**
     * Key length, 128 bits or 16 bytes or 4 words
     */
    private final int Nk = 4;

    /**
     * Number of columns
     */
    private final int Nb = 4;

    /**
     * Number of rounds
     */
    private final int Nr = 10;

    private byte[] input;
    private byte[] output;
    private byte[] key;
    private byte[] expandedKey;
    private AESError error;

    public AES(byte[] input, byte[] key) {
        this.input = input;
        this.key = key;
        this.error = AESError.noErr;
    }

    /**
     * Rotates a 32-bit word. That's a part of the rijndael key expansion core
     * @param word	A 32-bit word
     * @return	A 32-bit word
     */
    private byte[] rotate_word(byte[] word) {
        byte[] output = new byte[4];

        output[0] = word[1];
        output[1] = word[2];
        output[2] = word[3];
        output[3] = word[0];

        return output;
    }

    /**
     * Substitutes the word with the Rijndael S-Box
     * @param word	The word
     * @return	the substituted word
     */
    private byte[] substitute_word(byte[] word) {
        byte[] output = new byte[4];

        for (int i = 0; i < 4; i++) {
            byte currentByte = word[i];
            int row = (currentByte & 0xF0) >> 4;
            int col = (currentByte & 0x0F);

            output[i] = (byte)AESConst.sbox[row][col];
        }

        return output;
    }

    /**
     * Performs the Rijndael key espansion code
     * @param input	A 32-bit Word (4*1byte)
     * @param i	The iteration number
     * @return	A 32-Bit word
     */
    private byte[] keyScheduleCore(byte[] input, int i) throws Exception {
        if (input.length != 4) {
            error = AESError.no32BitWord;
            throw new Exception();
        }

        byte[] output = new byte[4];

        output = rotate_word(input);
        output = substitute_word(output);
        output[0] = (byte) (output[0] ^ AESConst.rcon[i]);

        return output;
    }

    /**
     * Generates a key schedule.
     * The Key Expansion generates a total of Nb (Nr + 1) words:
     * the algorithm requires an initial set of Nb words, and each of the Nr rounds requires Nb words of key data
     * @throws Exception
     * @return The expanded key
     */
    public byte[] expandKey() throws Exception {
        if (this.key.length != 16) {
            this.error = AESError.keyNot128Bits;
            throw new Exception();
        }

        // 16 * 1 byte = 128 bit
        // 128 bit * (Number of rounds + 1)
        // +1, da die erste Runde nicht als solche gezählt wird (nur AddRoundKey)
        // 16*11=176 bytes
        byte[] output = new byte[(Nb * 4) * (Nr + 1)];

        // The first Nk bytes of the expanded key are simply the encryption key.
        for (int i = 0; i < 4 * Nk; i++) {
            output[i] = this.key[i];
        }

        int len = Nb * (Nr + 1); // Nb = 32bit => 4 * 8bit = 4 * 1byte
        for (int i = Nk; i < len; i++) {
            byte[] tmpWord = new byte[] {
                    output[4*(i-1)+0],	output[4*(i-1)+1],
                    output[4*(i-1)+2],	output[4*(i-1)+3],
            };

            if (i % Nk == 0) {
                tmpWord = keyScheduleCore(tmpWord, i / Nk);
            } // For Nk > 128bit you would have another conditional substitution operation here

            // XOR word
            for (int j = 0; j < 4; j++) {
                output[4*i+j] = (byte)(output[4*(i-Nk)+j] ^ tmpWord[j]);
            }
        }

        return output;
    }

    public void encrypt() {
        try {
            this.expandedKey = expandKey();

            ArrayList<Byte> tmpOut = new ArrayList<>();

            // Auf 16-Byte Blöcke erweitern
            if(input.length % 16 != 0){
                byte[] tmp = new byte[((int)(Math.floor(input.length / 16) + 1) * 16)];
                for(int i = 0; i < input.length; i++){
                    tmp[i] = input[i];
                }
                input = tmp;
            }

            // AES auf alle Blöcke anwenden
            for(int i = 0; i < input.length / 16; i++){
                byte[][] state = new byte[][]{
                    {input[i*16], input[i*16+4], input[i*16+8],input[i*16+12]},
                    {input[i*16+1], input[i*16+5], input[i*16+9],input[i*16+13]},
                    {input[i*16+2], input[i*16+6], input[i*16+10],input[i*16+14]},
                    {input[i*16+3], input[i*16+7], input[i*16+11],input[i*16+15]}
                };

                tmpOut.addAll(cipher(state, false));
            }


            output = new byte[tmpOut.size()];
            for (int i = 0; i < tmpOut.size(); i++) {
                output[i] = tmpOut.get(i);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Führt AES auf einen Input-Block aus
     * @param state	Der Input als 2D Matrix
     * @param inverse	Bool, ob AES invertiert ausgeführt werden soll (entschlüsseln)
     * @return	Das Ergebnis
     * @throws Exception	Falls etwas nicht so gut gelaufen ist
     */
    public ArrayList<Byte> cipher(byte[][] state, boolean inverse) throws Exception {
        if (inverse) {
        	// Entschlüsseln
        	
            state = addRoundKey(state, getRoundKeyByNr(Nr), inverse);

            for(int i = Nr-1; i > 0; i--){
                state = shiftRows(state, inverse);
                state = subBytes(state, inverse);
                state = addRoundKey(state, getRoundKeyByNr(i), inverse);
                state = mixColumns(state, inverse);
            }

            state = shiftRows(state, inverse);
            state = subBytes(state, inverse);
            state = addRoundKey(state, getRoundKeyByNr(0), inverse);

        } else {
        	// Verschlüsseln
        	
            state = addRoundKey(state, getRoundKeyByNr(0), inverse);

            for(int i = 1; i < Nr; i++){
                state = subBytes(state, inverse);
                state = shiftRows(state, inverse);
                state = mixColumns(state, inverse);
                state = addRoundKey(state, getRoundKeyByNr(i), inverse);
            }

            state = subBytes(state, inverse);
            state = shiftRows(state, inverse);
            state = addRoundKey(state, getRoundKeyByNr(Nr), inverse);
        }

        
        // In eine ArrayList konvertieren
        ArrayList<Byte> tmp = new ArrayList<>();

        for ( int i = 0; i < state.length; i++ ){
            for ( int j = 0; j < state.length; j++ ){
                tmp.add(state[j][i]);
            }
        }

        return tmp;
    }

    /**
     * Entschlüsselt den Input
     */
    public void decrypt() {
        try {
            this.expandedKey = expandKey();

            ArrayList<Byte> tmpOut = new ArrayList<>();

            if(input.length % 16 != 0){
                byte[] tmp = new byte[((int)(Math.floor(input.length / 16) + 1) * 16)];
                for(int i = 0; i < input.length; i++){
                    tmp[i] = input[i];
                }
                input = tmp;
            }

            for(int i = 0; i < input.length / 16; i++){
                byte[][] state = new byte[][]{
                    {input[i*16], input[i*16+4], input[i*16+8],input[i*16+12]},
                    {input[i*16+1], input[i*16+5], input[i*16+9],input[i*16+13]},
                    {input[i*16+2], input[i*16+6], input[i*16+10],input[i*16+14]},
                    {input[i*16+3], input[i*16+7], input[i*16+11],input[i*16+15]}
                };

                tmpOut.addAll(cipher(state, true));
            }


            output = new byte[tmpOut.size()];
            for (int i = 0; i < tmpOut.size(); i++) {
                output[i] = tmpOut.get(i);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** 
     * AddRoundKey Schritt des Rjindel Algorithmus
     * @param state	Input
     * @param roundKey	Der Rundenschlüssel
     * @param inverse	Invertiert Ja/Nein?
     * @return	Das Ergebnis
     * @throws Exception
     */
    private byte[][] addRoundKey(byte[][] state, byte[] roundKey, boolean inverse) throws Exception
    {
        if ( state.length != roundKey.length/4 || state[0].length != roundKey.length/4 ){
            System.err.println(state.length + " | " + roundKey.length);
            this.error = AESError.unequalLengthError;
            throw new Exception();
        }

        byte[][] tmp = new byte[4][4];
        for ( int i = 0; i < state.length; i++ ){
            for ( int j = 0; j < state.length; j++ ){
                tmp[j][i] = (byte) (state[j][i] ^ roundKey[i*4+j]);
            }
        }
        return tmp;
    }

    private byte[][] subBytes(byte[][] state, boolean inverse) throws Exception
    {
        if(state == null){
            this.error = AESError.NullStateError;
            throw new Exception();
        }
        byte[][] out = new byte[4][4];

        for (int i = 0; i < 4; i++ ){
            for (int j = 0; j < 4; j++){ //i = Zeile, j = Spalte
                byte val = state[i][j];
                
                // Indices für die SBox kalkulieren
                int row = (val & 0xf0) >> 4;
                int col = val & 0x0f;
                if(inverse){
                    out[i][j] = (byte) AESConst.isbox[row][col];
                } else {
                    out[i][j] = (byte) AESConst.sbox[row][col];
                }
            }
        }
        return out;
    }

    private byte[][] shiftRows(byte[][] state, boolean inverse) throws Exception 
    {
        if(state == null){
            this.error = AESError.NullStateError;
            throw new Exception();
        }

        byte[][] out = new byte[4][4];

        if(!inverse){
            out = new byte[][]{
                {state[0][0], state[0][1], state[0][2], state[0][3]},
                {state[1][1], state[1][2], state[1][3], state[1][0]},
                {state[2][2], state[2][3], state[2][0], state[2][1]},
                {state[3][3], state[3][0], state[3][1], state[3][2]}
            };
        }else{
            out = new byte[][]{
                {state[0][0], state[0][1], state[0][2], state[0][3]},
                {state[1][3], state[1][0], state[1][1], state[1][2]},
                {state[2][2], state[2][3], state[2][0], state[2][1]},
                {state[3][1], state[3][2], state[3][3], state[3][0]}
            };
        }

        return out;
    }

    /**
     * Eine Multiplikation im GF(2^8)
     * @param one
     * @param two
     * @return
     */
    private byte gmult(int one, int two){
        byte a = (byte)one;
        byte b = (byte)two;

        byte p = 0x0, hbs = 0x0;
        for(int i = 0; i < 8; i++){
            if((b & 1) != 0){
                p ^= a;
            }
            hbs = (byte) (a & 0x80);
            a <<= 1;
            if(hbs != 0){
                a ^= 0x1b;
            }
            b >>= 1;
        }

        return p;
    }

    private byte[][] mixColumns(byte[][] state, boolean inverse){
        byte[][] out = new byte[4][4];

        for(int i = 0; i < 4; i++){
            int[] tmp = new int[]{
                state[0][i], state[1][i], state[2][i], state[3][i]
            };
            if(inverse){
                out[0][i] = (byte) (gmult(AESConst.inColMat[0][0], tmp[0]) ^ gmult(AESConst.inColMat[0][1], tmp[1]) ^ gmult(AESConst.inColMat[0][2], tmp[2]) ^ gmult(AESConst.inColMat[0][3], tmp[3]));
                out[1][i] = (byte) (gmult(AESConst.inColMat[1][0], tmp[0]) ^ gmult(AESConst.inColMat[1][1], tmp[1]) ^ gmult(AESConst.inColMat[1][2], tmp[2]) ^ gmult(AESConst.inColMat[1][3], tmp[3]));
                out[2][i] = (byte) (gmult(AESConst.inColMat[2][0], tmp[0]) ^ gmult(AESConst.inColMat[2][1], tmp[1]) ^ gmult(AESConst.inColMat[2][2], tmp[2]) ^ gmult(AESConst.inColMat[2][3], tmp[3]));
                out[3][i] = (byte) (gmult(AESConst.inColMat[3][0], tmp[0]) ^ gmult(AESConst.inColMat[3][1], tmp[1]) ^ gmult(AESConst.inColMat[3][2], tmp[2]) ^ gmult(AESConst.inColMat[3][3], tmp[3]));

            }
            else{
                out[0][i] = (byte) (gmult(AESConst.colMat[0][0], tmp[0]) ^ gmult(AESConst.colMat[0][1], tmp[1]) ^ gmult(AESConst.colMat[0][2], tmp[2]) ^ gmult(AESConst.colMat[0][3], tmp[3]));
                out[1][i] = (byte) (gmult(AESConst.colMat[1][0], tmp[0]) ^ gmult(AESConst.colMat[1][1], tmp[1]) ^ gmult(AESConst.colMat[1][2], tmp[2]) ^ gmult(AESConst.colMat[1][3], tmp[3]));
                out[2][i] = (byte) (gmult(AESConst.colMat[2][0], tmp[0]) ^ gmult(AESConst.colMat[2][1], tmp[1]) ^ gmult(AESConst.colMat[2][2], tmp[2]) ^ gmult(AESConst.colMat[2][3], tmp[3]));
                out[3][i] = (byte) (gmult(AESConst.colMat[3][0], tmp[0]) ^ gmult(AESConst.colMat[3][1], tmp[1]) ^ gmult(AESConst.colMat[3][2], tmp[2]) ^ gmult(AESConst.colMat[3][3], tmp[3]));
            }
        }

        return out;
    }

    /**
     * Gibt den Rundenschlüssel für die Rundennummer zurück
     * @param roundNr	Die Rundennummer
     * @return	Der Rundenschlüssel
     */
    private byte[] getRoundKeyByNr(int roundNr){
        byte[] roundK = new byte[16];
        for(int i = 0; i < 16; i++){
            roundK[i] =	expandedKey[roundNr*16+i];
        }
        return roundK;
    }

    public byte[] getInput() {
        return input;
    }

    public void setInput(byte[] input) {
        this.input = input;
    }

    public byte[] getOutput() {
        return output;
    }

    public void setOutput(byte[] output) {
        this.output = output;
    }

    public byte[] getKey() {
        return key;
    }

    public void setKey(byte[] key) {
        this.key = key;
    }

    public AESError getError() {
        return error;
    }

    public byte[] getExpandedKey() {
        return expandedKey;
    }
}
