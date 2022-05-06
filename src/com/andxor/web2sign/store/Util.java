package com.andxor.web2sign.store;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class Util {

    // token of 16*log2(62) = 95 bit of security
    private static final int token_length = 16;
    private static final char[] token_alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789".toCharArray();

    private static final SecureRandom srng = new SecureRandom();
    private static final int defaultBufferSize = 8192;

    /**
     * Copy an InputStream into an OutputStream
     *
     * @param is   the InputStream.
     * @param os   the OutputStream.
     * @throws IOException if an error occurred.
     */
    public static void inToOut(InputStream is, OutputStream os) throws IOException {
        byte[] buffer = new byte[defaultBufferSize];
        int len = 0;
        while ((len = is.read(buffer)) != -1)
            os.write(buffer, 0, len);
    }

    public static byte[] inToArray(InputStream is) throws IOException {
        int len;
        byte[] buf = new byte[defaultBufferSize];
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        while ((len = is.read(buf)) != -1)
            bos.write(buf, 0, len);
        return bos.toByteArray();
    }

    public static byte[] arrayToHash(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalError("SHA-256 should be available", e);
        }
    }

    public static final String DIGITS = "0123456789ABCDEF";

    public static String toHex(byte[] buffer) {
        if (buffer == null || buffer.length == 0)
            return "";
        StringBuilder str = new StringBuilder(buffer.length * 2);
        for (byte b : buffer) {
            str.append(DIGITS.charAt((b >> 4) & 0x0F));
            str.append(DIGITS.charAt(b & 0x0F));
        }
        return str.toString();
    }

    /**
     * Returns an unique character string which an attacker can't guess.
     *
     * @return random value
     */
    public static String uniqueToken() {
        char[] buf = new char[token_length];
        for (int i = 0; i < token_length; i++)
            buf[i] = token_alphabet[srng.nextInt(token_alphabet.length)];
        return new String(buf);
    }

    public static int random(int max) {
        return srng.nextInt(max);
    }

    public static File getFile(String file) {
        try {
            String root = (String) Util.getConfig().get("root");
            return new File(root, file);
        } catch (Exception e) {
            throw new RuntimeException("Configuration error in 'root'", e);
        }
    }

    public static File newFile(String base) {
        base = base.replaceFirst(".pdf$", "");
        int loop = 0;
        File f;
        do {
            f = getFile(String.format(base + ".ver-%04X.pdf", srng.nextInt(0x10000)));
            if (++loop > 20)
                throw new RuntimeException("should have found a free filename by now already");
        } while (f.exists());
        return f;
    }

    public static JSON.Obj getConfig() {
        try {
            byte[] raw = Util.inToArray(Objects.requireNonNull(Util.class.getResourceAsStream("/config.json")));
            return new JSON.Parser(new String(raw, StandardCharsets.UTF_8), true).getObject();
        } catch (Exception e) {
            throw new RuntimeException("Configuration error", e);
        }
    }

    protected final static Pattern specialChars = Pattern.compile("[&<>'\"]");

    /**
     * Escapes a string to be HTML 2.0 safe (ampersand, less than, greater than, apostrophe and quotes).
     * @param str raw string to be HTML-escaped
     * @return the escaped HTML string
     */
    public static String specialChars(CharSequence str) {
        if (str == null)
            return "";
        // would be nice to use m.appendReplacement() but it doesn't support StringBuilder until Java9
        StringBuilder out = new StringBuilder();
        Matcher m = specialChars.matcher(str);
        int pos = 0;
        while (m.find()) {
            int start = m.start();
            out.append(str, pos, start);
            switch (str.charAt(start)) {
            case '&':  out.append("&amp;");  break;
            case '<':  out.append("&lt;");   break;
            case '>':  out.append("&gt;");   break;
            case '\'': out.append("&#039;"); break; // "&apos;" is only defined starting from XHTML 1.0
            case '"':  out.append("&quot;"); break;
            default:   throw new RuntimeException("Switch out of sync with Pattern", null);
            }
            pos = m.end();
        }
        out.append(str, pos, str.length());
        return out.toString();
    }

}
