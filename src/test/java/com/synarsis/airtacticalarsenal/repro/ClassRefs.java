package com.synarsis.airtacticalarsenal.repro;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

/**
 * Reads the constant pool of a compiled class and exposes every symbolic reference it makes.
 *
 * <p>Used by repro tests that assert a *missing* call. "This handler never checks the forbidden
 * zone" is a claim about code that is not there, which no runtime assertion can observe: the
 * check simply never fires, and a passing launch looks identical to an unguarded one. Reading
 * the constant pool turns that absence into something a test can fail on, because a method a
 * class never calls has no {@code Methodref} entry for it.
 *
 * <p>Only the constant pool is parsed -- enough to see which methods a class can reach, without
 * a bytecode library on the test classpath. A reference here means the call site exists; it does
 * not prove the call executes on every path. That direction is fine for these tests: they assert
 * absence, and absence in the constant pool is conclusive.
 */
public final class ClassRefs {

    private static final int UTF8 = 1, INTEGER = 3, FLOAT = 4, LONG = 5, DOUBLE = 6,
            CLASS = 7, STRING = 8, FIELDREF = 9, METHODREF = 10, INTERFACE_METHODREF = 11,
            NAME_AND_TYPE = 12, METHOD_HANDLE = 15, METHOD_TYPE = 16, DYNAMIC = 17,
            INVOKE_DYNAMIC = 18, MODULE = 19, PACKAGE = 20;

    private final Set<String> utf8Constants;

    private ClassRefs(Set<String> utf8Constants) {
        this.utf8Constants = utf8Constants;
    }

    /** Parses the constant pool of {@code type} as loaded from the test classpath. */
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

    /**
     * True if the class's constant pool mentions {@code name}, i.e. the class has a call site
     * for (or otherwise symbolically references) that method or field name.
     */
    public boolean references(String name) {
        return utf8Constants.contains(name);
    }

    /**
     * True if any constant <em>contains</em> {@code fragment}.
     *
     * <p>{@link #references} matches a whole constant, which is what you want for a method or
     * field name. A package shows up as part of a longer string instead -- bare as
     * {@code net/minecraft/client/Minecraft}, and inside descriptors as
     * {@code (Lnet/minecraft/client/Minecraft;)V} -- so it needs a substring search.
     */
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
        in.readUnsignedShort(); // minor version
        in.readUnsignedShort(); // major version

        int poolCount = in.readUnsignedShort();
        Set<String> utf8 = new HashSet<>();

        // Constant pool is 1-indexed; long/double occupy two slots.
        for (int i = 1; i < poolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case UTF8 -> utf8.add(in.readUTF());
                case INTEGER, FLOAT, FIELDREF, METHODREF, INTERFACE_METHODREF,
                     NAME_AND_TYPE, DYNAMIC, INVOKE_DYNAMIC -> in.skipBytes(4);
                case LONG, DOUBLE -> {
                    in.skipBytes(8);
                    i++; // takes two constant pool entries
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
