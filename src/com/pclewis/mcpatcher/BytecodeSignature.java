package com.pclewis.mcpatcher;

import javassist.bytecode.ClassFile;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.MethodInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * ClassSignature that matches a particular bytecode sequence.
 */
abstract public class BytecodeSignature extends ClassSignature {
    /**
     * Optional method name.
     */
    protected String methodName = null;
    /**
     * Matcher object.
     *
     * @see BytecodeMatcher
     */
    protected BytecodeMatcher matcher;

    HashMap<Integer, JavaRef> xrefs = new HashMap<Integer, JavaRef>();

    /**
     * Generate a regular expression for the current method.
     *
     * @param methodInfo method object used with push and reference calls
     * @return String regex
     * @see ClassSignature#push(javassist.bytecode.MethodInfo, Object)
     * @see ClassSignature#reference(javassist.bytecode.MethodInfo, int, JavaRef)
     */
    abstract public String getMatchExpression(MethodInfo methodInfo);

    protected boolean match(MethodInfo methodInfo) {
        matcher = new BytecodeMatcher(getMatchExpression(methodInfo));
        return matcher.match(methodInfo);
    }

    public boolean match(String filename, ClassFile classFile, ClassMap tempClassMap) {
        for (Object o : classFile.getMethods()) {
            MethodInfo methodInfo = (MethodInfo) o;
            CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
            if (codeAttribute != null && match(methodInfo)) {
                if (methodName != null) {
                    String deobfName = classMod.getDeobfClass();
                    tempClassMap.addClassMap(deobfName, ClassMap.filenameToClassName(filename));
                    tempClassMap.addMethodMap(deobfName, methodName, methodInfo.getName());
                }
                ConstPool constPool = methodInfo.getConstPool();
                for (Map.Entry<Integer, JavaRef> entry : xrefs.entrySet()) {
                    int captureGroup = entry.getKey();
                    JavaRef xref = entry.getValue();
                    byte[] code = matcher.getCaptureGroup(captureGroup);
                    int index = Util.demarshal(code, 1, 2);
                    ConstPoolUtils.matchOpcodeToRefType(code[0], xref);
                    ConstPoolUtils.matchConstPoolTagToRefType(constPool.getTag(index), xref);
                    tempClassMap.addMap(xref, ConstPoolUtils.getRefForIndex(constPool, index));
                }
                afterMatch(classFile, methodInfo);
                return true;
            }
        }
        return false;
    }

    /**
     * Assigns a name to a signature.  On matching, the target class and method will be added.
     * to the class map.
     *
     * @param methodName descriptive name of method
     * @return this
     */
    public BytecodeSignature setMethodName(String methodName) {
        this.methodName = methodName;
        return this;
    }

    /**
     * Adds a class cross-reference to a bytecode signature.  After a match, the const pool reference
     * in the capture group will be added to the class map.
     *
     * @param captureGroup matcher capture group
     * @param javaRef      field/method ref using descriptive names
     * @return this
     */
    public BytecodeSignature addXref(int captureGroup, JavaRef javaRef) {
        xrefs.put(captureGroup, javaRef);
        return this;
    }

    /**
     * Called immediately after a successful match.  Gives an opportunity to extract bytecode
     * values using getCaptureGroup, for example.
     *
     * @param classFile  matched class file
     * @param methodInfo matched method
     */
    public void afterMatch(ClassFile classFile, MethodInfo methodInfo) {
    }
}
