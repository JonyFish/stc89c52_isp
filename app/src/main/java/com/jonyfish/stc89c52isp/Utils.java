package com.jonyfish.stc89c52isp;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;

import java.nio.ByteBuffer;
import java.util.Arrays;

public class Utils {
	public static int getInt(byte hig, byte low) {
        return ((hig & 0xff) << 8) + (low & 0xff);
    }


	public static byte[] sub(byte[] arr, int start, int end) {
        if (start < 0) {
            start = arr.length + start;
        }
        if (end < 0) {
            end = arr.length + end;
        }
        try {
            return Arrays.copyOfRange(arr, start, end);

        } catch (Exception e) {
            //System.err.printf(" Error sub(%s,%d,%d) \n", HexUtils.toString(arr),start,end);
            e.printStackTrace();
            return null;
        }
    }


	public static byte[] data(ByteBuffer byteBuffer) {
        return Arrays.copyOfRange(byteBuffer.array(), 0, byteBuffer.position());
    }

	public static String hex2String(byte[] src) {
        String strHex = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < src.length; n++) {
            strHex = Integer.toHexString(src[n] & 0xFF);
            sb.append((strHex.length() == 1) ? "0" + strHex : strHex);
            sb.append(" ");

        }
        return sb.toString().trim().toUpperCase();
    }


	public static byte[] create(int len, byte value) {
        byte[] bytes = new byte[len];
        Arrays.fill(bytes, value);
        return bytes;
    }
	
	public static byte[] file2byte(String filePath) {
        byte[] buffer = null;
        try {
            File file = new File(filePath);
            FileInputStream fis = new FileInputStream(file);
            System.out.println("reading : " + fis.available());
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[2048];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return buffer;
    }
	

}
