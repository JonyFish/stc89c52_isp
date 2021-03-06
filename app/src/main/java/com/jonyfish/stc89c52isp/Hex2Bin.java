package com.jonyfish.stc89c52isp;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.RandomAccessFile;
import java.util.HashSet;
import java.util.Set;

public class Hex2Bin {
    private static final String TAG = Hex2Bin.class.getSimpleName();
    private final static int REC_TYPE_DATA = 0;
    private final static int REC_TYPE_END_OF_FILE = 1;
    private final static int REC_TYPE_EXTEND_SEG_ADDR = 2;
    private final static int REC_TYPE_START_SEG_ADDR = 3;
    private final static int REC_TYPE_EXTEND_LINEAR_ADDR = 4;
    private final static int REC_TYPE_START_LINEAR_ADDR = 5;
    private final static String SYMBOL_NEWLINE = System.getProperty("line.separator");

    public static File conv(String path) {
        File inFile = new File(path);
        File outFile = new File(path.replace(".hex", ".bin"));
        if (outFile.exists()) {
            System.out.println( "conv: delete exist "+outFile);
            outFile.delete();
        }
        BufferedReader reader = null;
        RandomAccessFile raf = null;

        String line = null;
        int numofBytes = 0;
        int numofLine = 0;
        byte checksum = 0;
        int indexInLine = 0;
        int segAddr = 0;
        int baseAddr = 0;
        int addr = 0;
        int recType = 0;
        int dataByte;
        int CS = 0;
        int IP = 0;
        int EIP = 0; // start address;
        boolean ifoffset = false;
        /*if (args[2].equals("-offset")) {
		 ifoffset = true;
		 }*/

        // hashset
        Set<String> set = new HashSet<String>();
        try {
            reader = new BufferedReader(new FileReader(inFile));
            raf = new RandomAccessFile(outFile, "rw");
            while ((line = reader.readLine()) != null) {
                numofLine++;
                // Start code: first character must be ':'
                if (line.charAt(0) != ':') {
                    System.err.printf("Bad format on line %d: %s\n", numofLine, line);
                    System.exit(-1);
                }

                indexInLine = 0;
                checksum = 0;
                indexInLine++;

                // Byte count: next two characters indicate the number of bytes
                // in data field
                numofBytes = getByte(line, indexInLine);
                if (numofBytes == 0) {
                    break;
                }
                checksum += numofBytes;
                indexInLine += 2;

                // Address: next four characters indicate the address
                addr = getWord(line, indexInLine);
                checksum += getByte(line, indexInLine);
                checksum += getByte(line, indexInLine + 2);
                indexInLine += 4;

                // Record type: next two characters indicate type of the record
                recType = getByte(line, indexInLine);
                checksum += recType;
                indexInLine += 2;
                set.add("0" + recType);
                switch (recType) {
                    case REC_TYPE_DATA:
                        addr = (segAddr << 4) + baseAddr + addr;
                        for (int i = 0; i < numofBytes; i++) {
                            dataByte = getByte(line, indexInLine);
                            checksum += dataByte;
                            indexInLine += 2;
                            raf.seek(addr);
                            raf.writeByte(dataByte);
                            addr++;
                        }
                        break;
                    case REC_TYPE_END_OF_FILE:
                        break;
                    case REC_TYPE_EXTEND_SEG_ADDR:
                        segAddr = getWord(line, indexInLine);
                        checksum += getByte(line, indexInLine);
                        checksum += getByte(line, indexInLine + 2);
                        indexInLine += 4;
                        System.out.printf("Line %d:The Segment Address changes into 0x%08x.%s", numofLine, segAddr,
										  SYMBOL_NEWLINE);
                        break;
                    case REC_TYPE_START_SEG_ADDR:
                        /*
                         * For 80x86 processors, specifies the initial content of
                         * the CS:IP registers. The address field is 0000, the byte
                         * count is 04, the first two bytes are the CS value, the
                         * latter two are the IP value.
                         */
                        CS = getByte(line, indexInLine);
                        checksum += CS;
                        IP = getByte(line, indexInLine + 2);
                        checksum += IP;
                        indexInLine += 4;

                        System.out.printf("Line %d:The CS change to 0x%08x.%s", numofLine, CS, SYMBOL_NEWLINE);
                        System.out.printf("Line %d:The IP change to 0x%08x.%s", numofLine, IP, SYMBOL_NEWLINE);
                        break;
                    case REC_TYPE_EXTEND_LINEAR_ADDR:
                        // 04
                        if (ifoffset) {
                            baseAddr = (getWord(line, indexInLine) << 16);
                        }
                        checksum += getByte(line, indexInLine);
                        checksum += getByte(line, indexInLine + 2);
                        indexInLine += 4;
                        System.out.printf("Line %d:The Linear Base Address changes into 0x%08x.%s", numofLine, baseAddr,
										  SYMBOL_NEWLINE);
                        break;
                    case REC_TYPE_START_LINEAR_ADDR:
                        // 05
                        /*
                         * The four data bytes represent the 32-bit value loaded
                         * into the EIP register of the 80386 and higher CPU.
                         */
                        EIP = (getWord(line, indexInLine) << 16) + getWord(line, indexInLine + 4);
                        checksum += getByte(line, indexInLine);
                        checksum += getByte(line, indexInLine + 2);
                        checksum += getByte(line, indexInLine + 4);
                        checksum += getByte(line, indexInLine + 6);
                        indexInLine += 8;
                        System.out.printf("Line %d:The EIP change to 0x%08x.%s", numofLine, EIP, SYMBOL_NEWLINE);
                        break;
                    default:
                        System.out.printf("Record type isn't in 0-5.%s", SYMBOL_NEWLINE);
                        break;
                }

                checksum = (byte) (~checksum + 1);
                // Checksum : the last character is checksum
                byte actualChecksum = (byte) getByte(line, indexInLine);
                if (checksum != actualChecksum) {
                    System.out.printf("Checksum mismatch on line %d: %02x vs %02x.%s", numofLine, checksum,
									  actualChecksum, SYMBOL_NEWLINE);
                }
            }
            System.out.printf("The following Record Types has emerged:%s %s", set.toString(), SYMBOL_NEWLINE);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                if (raf != null) {
                    raf.close();
                }
                if (reader != null) {
                    reader.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }

        }
        return outFile;
    }

    public static int getnybble(String str, int index) {
        int value = 0;
        char c = str.charAt(index);
        if (c >= '0' && c <= '9') {
            value = c - '0';
        } else if (c >= 'a' && c <= 'f') {
            value = c - 'a' + 10;
        } else if (c >= 'A' && c <= 'F') {
            value = c - 'A' + 10;
        }
        return value;
    }

    public static int getByte(String str, int index) {
        return (getnybble(str, index) << 4) + getnybble(str, index + 1);
    }

    public static int getWord(String str, int index) {
        return (getByte(str, index) << 8) + getByte(str, index + 2);
    }
}
