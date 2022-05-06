package com.andxor.web2sign.store;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Array;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * JSON utility class.
 *
 * @author Lapo Luchini <l.luchini@andxor.it>
 */
public final class JSON {

    private final static char BOM = 65279; // U+FEFF

    public static String encode(Object o, boolean unicode, boolean pretty) {
        try {
            StringWriter s = new StringWriter();
            encode(s, o, unicode, pretty ? "\n" : null);
            return s.toString();
        } catch (IOException e) {
            throw (InternalError) new InternalError("StringWriter should not throw IOException").initCause(e);
        }
    }

    public static String encode(Object o, boolean unicode) {
        return encode(o, unicode, false);
    }

    public static void encode(OutputStream os, Object o, boolean unicode, boolean pretty) throws IOException {
        OutputStreamWriter osw = new OutputStreamWriter(os, unicode ? "UTF-8" : "ASCII");
        encode(osw, o, unicode, pretty ? "\n" : null);
        osw.close();
    }

    public static void encode(OutputStream os, Object o, boolean unicode) throws IOException {
        encode(os, o, unicode, false);
    }

    public static void encode(Writer w, Object o, boolean unicode, boolean pretty) throws IOException {
        encode(w, o, unicode, pretty ? "\n" : null);
    }

    private static void writeChar(final Writer w, final boolean unicode, final char c) throws IOException {
        switch (c) {
            case '\\':
            case '"':
                w.append('\\');
                w.append(c);
                break;
            // ignoring \b and \f
            case '\n':
                w.append("\\n");
                break;
            case '\r':
                w.append("\\r");
                break;
            case '\t':
                w.append("\\t");
                break;
            default:
                if (c >= 32 && (unicode || c <= 126))
                    w.append(c);
                else
                    w.append(String.format("\\u%04X", (int) c));
        }
    }

    /**
     * Encode a Java structure into a JSON string.
     * 
     * @param w Stream to write the output to.
     * @param o Object to encode
     * @param unicode Allow using any unicode character. (if false anything not ASCII will be escaped)
     * @throws IOException
     */
    @SuppressWarnings("rawtypes")
    private static void encode(final Writer w, final Object o, final boolean unicode, String indent) throws IOException {
        String indent2 = (indent == null) ? null : indent + "  ";
        if (o == null) {
            w.append("null");
        } else if (o instanceof CharSequence) {
            w.append('"');
            CharSequence seq = (CharSequence) o;
            int len = seq.length();
            for (int i = 0; i < len; ++i) {
                char c = seq.charAt(i);
                writeChar(w, unicode, c);
            }
            w.append('"');
        } else if (o instanceof Reader) {
            w.append('"');
            Reader r = (Reader) o;
            int c;
            while ((c = r.read()) != -1)
                writeChar(w, unicode, (char) c);
            w.append('"');
        } else if (o instanceof Boolean) {
            w.append((Boolean) o ? "true" : "false");
        } else if (o instanceof Number) {
            w.append(o.toString().replaceFirst("[.]0$", ""));
        } else if (o instanceof Map) {
            w.append('{');
            boolean comma = false;
            for (Object o1 : ((Map) o).entrySet()) {
                if (comma)
                    w.append(',');
                else
                    comma = true;
                if (indent != null)
                    w.append(indent2);
                Map.Entry e = (Map.Entry) o1; //TODO: why needed?
                Object k = e.getKey();
                if (!(k instanceof CharSequence))
                    throw new IllegalArgumentException("Keys must be strings, found instead: " + ((k == null) ? "null" : k.getClass().getName()));
                encode(w, k, unicode, indent2);
                w.append(':');
                if (indent != null)
                    w.append(' ');
                encode(w, e.getValue(), unicode, indent2);
            }
            if (indent != null && comma)
                w.append(indent);
            w.append('}');
        } else if (o instanceof Iterable) {
            w.append('[');
            boolean comma = false;
            for (Object o1 : (Iterable) o) {
                if (comma)
                    w.append(',');
                else
                    comma = true;
                encode(w, o1, unicode, indent2);
            }
            w.append(']');
        } else if (o instanceof Object[]) { // any object array
            w.append('[');
            boolean comma = false;
            for (Object o1 : (Object[]) o) {
                if (comma)
                    w.append(',');
                else
                    comma = true;
                encode(w, o1, unicode, indent2);
            }
            w.append(']');
        } else if (o.getClass().isArray()) { // any native array
            int len = Array.getLength(o);
            w.append('[');
            boolean comma = false;
            for (int i = 0; i < len; ++i) {
                if (comma)
                    w.append(',');
                else
                    comma = true;
                encode(w, Array.get(o, i), unicode, indent2);
            }
            w.append(']');
        } else
            throw new IllegalArgumentException("Cannot encode: " + o.getClass().getName());
    }

    public static Object cloneDeep(Object o) {
        if (o == null) {
            return null;
        } else if (o instanceof String) {
            return o; // it's immutable anyways
        } else if (o instanceof CharSequence) {
            return ((CharSequence) o).toString();
        } else if (o instanceof Boolean) {
            return o; // immutable
        } else if (o instanceof Number) {
            return o; // immutable
        } else if (o instanceof Map) {
            Obj u = new Obj();
            for (Object o1 : ((Map) o).entrySet()) {
                Map.Entry e = (Map.Entry) o1; //TODO: why needed?
                Object k = e.getKey();
                if (!(k instanceof String))
                    throw new IllegalArgumentException("Keys must be strings, found instead: " + ((k == null) ? "null" : k.getClass().getName()));
                u.put((String) k, cloneDeep(e.getValue()));
            }
            return u;
        } else if (o instanceof Iterable) {
            ArrayList u = new ArrayList();
            for (Object o1 : (Iterable) o)
                u.add(cloneDeep(o1));
            return u;
        } else if (o instanceof Object[]) { // any object array
            Object[] u = ((Object[]) o).clone();
            for (int i = 0; i < u.length; ++i)
                u[i] = cloneDeep(u[i]);
            return u;
        } else if (o.getClass().isArray()) { // any native array
            int len = Array.getLength(o);
            Object u = Array.newInstance(o.getClass().getComponentType(), len);
            System.arraycopy(o, 0, u, 0, len);
            return u;
        } else
            throw new IllegalArgumentException("Cannot deepClone class: " + o.getClass().getName());
    }

    public final static class Obj extends LinkedHashMap<String, Object> {

        /**
         * Parse a JSON string.
         * This allows the class to be used as a {@link javax.ws.rs.FormParam}.
         * @param s a JSON object
         * @return the parsed object
         * @throws Parser.Exception when JSON format is not valid
         */
        public static Obj valueOf(String s) throws Parser.Exception {
            return new Parser(s).getObject();
        }

        public static Obj wrap(Map<? extends String, ? extends Object> a) {
            Obj o = new Obj();
            o.putAll(a);
            return o;
        }

        public static Obj merge(Obj... as) {
            Obj o = new Obj();
            for (Obj a : as)
                if (a != null)
                    o.putAll(a);
            return o;
        }

        public static Obj mergeDeep(Obj... as) {
            Obj o = new Obj();
            for (Obj a : as)
                if (a != null) {
                    for (Map.Entry<String, Object> e : a.entrySet()) {
                        String k = e.getKey();
                        Object vNew = e.getValue();
                        Object vOld = o.get(k);
                        if (vNew instanceof Obj && vOld instanceof Obj) {
                            o.put(k, mergeDeep((Obj) vOld, (Obj) vNew));
                        } else // any other value simply overwrites
                            o.put(k, vNew);
                    }
                }
            return o;
        }

        public Obj      getObj(String key)    { return (Obj)      super.get(key); }
        public String   getString(String key) { return (String)   super.get(key); }
        public Number   getNumber(String key) { return (Number)   super.get(key); }
        public Boolean  getBool(String key)   { return (Boolean)  super.get(key); }
        public Object[] getArray(String key)  {
            Object o = super.get(key);
            if (o instanceof List)
                return ((List) o).toArray();
            return (Object[]) super.get(key);
        }

        /** Get a copy of the array with a specific type.
         * Beware: changing the array elements will not change the original array's elements.
         * @throws ArrayStoreException if content is not compatible with the given type */
        public <T> T[]  getArrayCopy(String key, Class<? extends T[]> type)  {
            Object[] orig = getArray(key);
            T[] copy = (T[]) Array.newInstance(type.getComponentType(), orig.length);
            System.arraycopy(orig, 0, copy, 0, orig.length);
            return copy;
        }

        /** Gets (or creates) the specified object node. */
        public Obj forceObj(String key) {
            Obj o = getObj(key);
            if (o == null) {
                o = new Obj();
                put(key, o);
            }
            return o;
        }

        /** Gets the specified object node, throws {@link MissingFieldException} if missing or <code>null</code>. */
        public Obj requireObj(String key) throws MissingFieldException {
            Obj o = getObj(key);
            if (o == null)
                throw new MissingFieldException(key);
            return o;
        }

        /** Sets one or more parameters, returns this. */
        public Obj set(Object... o) {
            if ((o.length & 1) != 0)
                throw new IllegalArgumentException("Must have even arity.");
            for (int i = 0; i < o.length; i += 2) {
                if (!(o[i] instanceof CharSequence))
                    throw new IllegalArgumentException("Even argument #" + i + " is not a string.");
                put(o[i].toString(), o[i + 1]);
            }
            return this;
        }

        /** Sets value only if not null, else remove it. */
        public Object putNotNull(String key, Object value) {
            if (value == null)
                return remove(key);
            else
                return put(key, value);
        }

        public Obj cloneDeep() {
            return (Obj) JSON.cloneDeep(this);
        }

        public static class MissingFieldException extends Exception {
            private MissingFieldException(String error) {
                super(error);
            }
        }

    }

    public static Obj obj(Object... o) {
        return new Obj().set(o);
    }

    /**
     * Decodes a JSON string into Java objects.
     *
     * @author lapo
     */
    public final static class Parser {

        private final CharSequence s;
        private final boolean permissive;
        private int pos;

        public class Exception extends ParseException {

            private Exception(String error) {
                super(error + context(), pos);
            }

        }

        private String context() {
            final int len = s.length();
            if (pos > len) pos = len;
            pos = Math.min(s.length(), pos); // might be past end on "Unexpected end of input"
            int min = Math.max(0, pos - 50);
            int max = Math.min(len, pos + 50);
            String line1 = s.subSequence(min,  pos).toString().replaceAll("\\s+", " ");
            String line2 = s.subSequence(pos, max).toString().replaceAll("\\s+", " ");
            char[] arrow = new char[line1.length()];
            Arrays.fill(arrow, '-');
            return " in position " + pos + ":\r\n" + line1 + line2 + "\r\n" + new String(arrow) + '^';
        }

        public Parser(CharSequence seq) {
            this(seq, false);
        }

        public Parser(CharSequence seq, boolean permissive) {
            s = seq;
            this.permissive = permissive;
            pos = 0;
            if (permissive && seq.charAt(0) == BOM)
                ++pos;
        }

        private void skipComments() {
            // skip comments (both // and /* */)
            while (s.charAt(pos) == '/') {
                if (s.charAt(pos + 1) == '/') { // single line comments
                    ++pos;
                    while (s.charAt(pos) != '\n')
                        ++pos;
                    ++pos;
                } else if (s.charAt(pos + 1) == '*') { // multi-line comments
                    ++pos;
                    while (s.charAt(pos) != '*' || s.charAt(pos + 1) != '/')
                        ++pos;
                    pos += 2;
                } else // anything else isn't a comment and should be ignored
                    return;
                while (Character.isWhitespace(s.charAt(pos)))
                    ++pos;
            }
        }

        private void skipWhitespace() {
            try {
                while (Character.isWhitespace(s.charAt(pos)))
                    ++pos;
                if (permissive) skipComments();
            } catch (IndexOutOfBoundsException e) {
                // ignore, we won't ignore whitespace past EOF
            }
        }

        private char peek() throws Exception {
            try {
                return s.charAt(pos);
            } catch (IndexOutOfBoundsException e) {
                throw new Exception("Unexpected end of input");
            }
        }

        private char pop() throws Exception {
            try {
                return s.charAt(pos++);
            } catch (IndexOutOfBoundsException e) {
                throw new Exception("Unexpected end of input");
            }
        }

        private void eat(char expected) throws Exception {
            char k = peek();
            if (k != expected)
                throw new Exception("Expected '" + expected + "', found '" + k + "'");
            ++pos;
        }

        private boolean canEat(char expected) throws Exception {
            char k = peek();
            if (k != expected)
                return false;
            ++pos;
            return true;
        }

        private int eat2(char expected1, char expected2) throws Exception {
            char k = peek();
            if (k != expected1 && k != expected2)
                throw new Exception("Expected '" + expected1 + "' or '" + expected2 + "', found '" + k + "'");
            ++pos;
            return (k == expected1) ? 1 : 2;
        }

        private int parseHex(char k) throws Exception {
            if (k >= '0' && k <= '9')
                return (k - '0');
            if (k >= 'a' && k <= 'f')
                return (k - 'a' + 10);
            if (k >= 'A' && k <= 'F')
                return (k - 'A' + 10);
            throw new Exception("Expected hexadecimal digit, found '" + k + "'");
        }

        public String getString() throws Exception {
            skipWhitespace();
            eat('"');
            StringBuilder o = new StringBuilder();
            while (true) {
                char k = pop();
                switch (k) {
                case '"':
                    return o.toString();
                case '\\':
                    k = pop();
                    switch (k) {
                    case '\"':
                    case '\\':
                    case '/':
                        o.append(k);
                        break;
                    case 'b': o.append('\b'); break;
                    case 'f': o.append('\f'); break;
                    case 'n': o.append('\n'); break;
                    case 'r': o.append('\r'); break;
                    case 't': o.append('\t'); break;
                    case 'u':
                        k = pop();
                        int codepoint;
                        if (k == '{') { // ECMAScript 6: Unicode code point escapes
                            codepoint = parseHex(pop());
                            while (peek() != '}') {
                                codepoint = (codepoint << 4) | parseHex(pop());
                                if (codepoint > 0x10FFFF)
                                    throw new Exception("Unicode code point maximum value is \\u{10FFFF}");
                            } while (peek() != '}');
                            eat('}');
                        } else {
                            codepoint = parseHex(k);
                            for (int i = 0; i < 3; ++i)
                                codepoint = (codepoint << 4) | parseHex(pop());
                        }
                        o.append(Character.toChars(codepoint));
                        break;
                    }
                    break;
                default:
                    o.append(k);
                }
            }
        }

        private static final Pattern NUMBER = Pattern.compile("-?(?:0|[1-9][0-9]*)((?:[.][0-9]+)?(?:[eE][+-]?[0-9]+)?)");
        public Number getNumber() throws Exception {
            skipWhitespace();
            char k = peek();
            if (k != '-' && (k < '0' || k > '9'))
                throw new Exception("Expected number, found '" + k + "'");
            Matcher m = NUMBER.matcher(s);
            if (m.find(pos)) {
                if (m.start() != pos) // using a "^…" Pattern could avoid this, but it wouldn't match on find(pos > 0)
                    throw new Exception("Invalid number");
                pos = m.end();
                final String numStr = m.group();
                if (m.group(1).length() == 0) try {
                    long v = Long.parseLong(numStr);
                    if (v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE)
                        return (int) v;
                    return v;
                } catch (NumberFormatException e) {
                    // should only happen for integral values over 64 bit, fall-thru to parse them as Double
                }
                return Double.parseDouble(numStr);
            } else
                throw new Exception("Invalid number");
        }

        public Obj getObject() throws Exception {
            skipWhitespace();
            eat('{');
            skipWhitespace();
            Obj o = new Obj();
            if (canEat('}'))
                return o;
            do {
                if (permissive) {
                    skipWhitespace();
                    if (canEat('}')) break;
                }
                String key = getString();
                skipWhitespace();
                eat(':');
                skipWhitespace();
                Object value = getAny();
                skipWhitespace();
                o.put(key, value);
            } while (eat2(',', '}') == 1);
            return o;
        }

        public Object[] getArray() throws Exception {
            skipWhitespace();
            eat('[');
            skipWhitespace();
            if (canEat(']'))
                return new Object[0];
            ArrayList<Object> l = new ArrayList<Object>();
            do {
                if (permissive) {
                    skipWhitespace();
                    if (canEat(']')) break;
                }
                l.add(getAny());
                skipWhitespace();
            } while (eat2(',', ']') == 1);
            return l.toArray(new Object[l.size()]);
        }

        public Object getAny() throws Exception {
            skipWhitespace();
            switch (peek()) {
            case '"':
                return getString();
            case '-': case '0': case '1': case '2':
            case '3': case '4': case '5': case '6':
            case '7': case '8': case '9':
                return getNumber();
            case '{':
                return getObject();
            case '[':
                return getArray();
            case 't':
                eat('t'); eat('r'); eat('u'); eat('e');
                return Boolean.TRUE;
            case 'f':
                eat('f'); eat('a'); eat('l'); eat('s'); eat('e');
                return Boolean.FALSE;
            case 'n':
                eat('n'); eat('u'); eat('l'); eat('l');
                return null;
            default:
                throw new Exception("Unexpected '" + peek() + "'");
            }
        }
    }

}
