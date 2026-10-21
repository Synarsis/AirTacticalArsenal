package com.synarsis.airtacticalarsenal.repro;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

public final class ClassRefs {

    private static final int UTF8 = 1, INTEGER = 3, FLOAT = 4, LONG = 5, DOUBLE = 6,
            CLASS = 7, STRING = 8, FIELDREF = 9, METHODREF = 10, INTERFACE_METHODREF = 11,
            NAME_AND_TYPE = 12, METHOD_HANDLE = 15, METHOD_TYPE = 16, DYNAMIC = 17,
            INVOKE_DYNAMIC = 18, MODULE = 19, PACKAGE = 20;

    private final Set<String> utf8Constants;

    private ClassRefs(Set<String> utf8Constants) {
        this.utf8Constants = utf8Constants;
    }

    public static ClassRefs of(Class<?> type) {
        String resource = type.getName().replace('.', '/') + ".class";
        try (InputStream in = type.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("class file not found on the classpath: " + resource);
            }
            return new ClassRefs(readUtf8Constants(new DataInputStream(in)));
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + resource, e);
        }
    }

    public boolean references(String name) {
        return utf8Constants.contains(name);
    }

    public boolean referencesAnythingIn(String fragment) {
        for (String constant : utf8Constants) {
            if (constant.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> readUtf8Constants(DataInputStream in) throws IOException {
        if (in.readInt() != 0xCAFEBABE) {
            throw new IllegalStateException("not a class file");
        }
        in.readUnsignedShort();
        in.readUnsignedShort();

        int poolCount = in.readUnsignedShort();
        Set<String> utf8 = new HashSet<>();

        for (int i = 1; i < poolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case UTF8 -> utf8.add(in.readUTF());
                case INTEGER, FLOAT, FIELDREF, METHODREF, INTERFACE_METHODREF,
                     NAME_AND_TYPE, DYNAMIC, INVOKE_DYNAMIC -> in.skipBytes(4);
                case LONG, DOUBLE -> {
                    in.skipBytes(8);
                    i++;
                }
                case CLASS, STRING, METHOD_TYPE, MODULE, PACKAGE -> in.skipBytes(2);
                case METHOD_HANDLE -> in.skipBytes(3);
                default -> throw new IllegalStateException("unknown constant pool tag " + tag
                        + " at index " + i);
            }
        }
        return utf8;
    }
}
