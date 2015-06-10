/*
 * Copyright (c) 2014, Regents of the University of California
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package edu.uci.python.nodes.optimize;

import java.io.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.frame.*;
import com.oracle.truffle.api.nodes.*;

import edu.uci.python.nodes.*;
import edu.uci.python.nodes.call.*;
import edu.uci.python.nodes.control.*;
import edu.uci.python.nodes.frame.*;
import edu.uci.python.nodes.function.*;
import edu.uci.python.nodes.generator.*;
import edu.uci.python.runtime.*;

public class BuiltinIntrinsifier {

    @SuppressWarnings("unused") private final PythonContext context;
    @SuppressWarnings("unused") private final Assumption globalScopeUnchanged;
    @SuppressWarnings("unused") private final Assumption builtinModuleUnchanged;

    private final PythonCallNode callNode;
    private GeneratorExpressionNode genexp;

    public BuiltinIntrinsifier(PythonContext context, Assumption globalScopeUnchanged, Assumption builtinModuleUnchanged) {
        this.context = context;
        this.globalScopeUnchanged = globalScopeUnchanged;
        this.builtinModuleUnchanged = builtinModuleUnchanged;
        this.callNode = null;
        assert PythonOptions.IntrinsifyBuiltinCalls;
    }

    public BuiltinIntrinsifier(PythonContext context, Assumption globalScopeUnchanged, Assumption builtinModuleUnchanged, PythonCallNode callNode) {
        this.context = context;
        this.globalScopeUnchanged = globalScopeUnchanged;
        this.builtinModuleUnchanged = builtinModuleUnchanged;
        this.callNode = callNode;
        assert PythonOptions.IntrinsifyBuiltinCalls;
    }

    public void synthesize() {
        CompilerAsserts.neverPartOfCompilation();

        if (isCallerGenerator()) {
            return;
        }

        IntrinsifiableBuiltin target = IntrinsifiableBuiltin.findIntrinsifiable(callNode.getCalleeName());
        assert target != null;

        if (isArgumentGeneratorExpression()) {
            transformToComprehension(target);
        }
    }

    public boolean isCallerGenerator() {
        Node current = callNode;
        while (!(current instanceof ReturnTargetNode || current instanceof ModuleNode)) {
            current = current.getParent();
        }

        if (current instanceof GeneratorReturnTargetNode) {
            return true;
        }

        return false;
    }

    private boolean isArgumentGeneratorExpression() {
        if (callNode.getArgumentsNode().length() != 1) {
            return false;
        }

        PNode arg = callNode.getArgumentsNode().getArguments()[0];
        if (arg instanceof GeneratorExpressionNode) {
            genexp = (GeneratorExpressionNode) arg;
            return true;
        }

        return false;
    }

    private void transformToComprehension(IntrinsifiableBuiltin target) {
        FrameDescriptor genexpFrame = genexp.getFrameDescriptor();
        FrameDescriptor enclosingFrame = genexp.getEnclosingFrameDescriptor();
        PNode genexpBody = ((FunctionRootNode) genexp.getFunctionRootNode()).copy().getBody();
        genexpBody = NodeUtil.findFirstNodeInstance(genexpBody, ForNode.class);

        for (FrameSlot genexpSlot : genexpFrame.getSlots()) {
            if (genexpSlot.getIdentifier().equals("<return_val>")) {
                continue;
            }

            // Name does not collide
            // assert enclosingFrame.findFrameSlot(genexpSlot.getIdentifier()) == null;
            FrameSlot enclosingSlot = enclosingFrame.findOrAddFrameSlot(genexpSlot.getIdentifier());

            redirectLocalRead(genexpSlot, enclosingSlot, genexpBody);
            redirectLocalWrite(genexpSlot, enclosingSlot, genexpBody);
        }

        redirectLevelRead(genexpBody);

        FrameSlot listCompSlot = enclosingFrame.addFrameSlot("<" + target.getName() + "_comp_val" + genexp.hashCode() + ">");
        YieldNode yield = NodeUtil.findFirstNodeInstance(genexpBody, YieldNode.class);
        WriteLocalVariableNode write = (WriteLocalVariableNode) yield.getRhs();
        yield.replace(target.createComprehensionAppendNode(listCompSlot, write.getRhs()));
        callNode.replace(target.createComprehensionNode(listCompSlot, genexpBody));

        genexp.setAsOptimized();
        PrintStream out = System.out;
        out.println("[ZipPy] builtin intrinsifier: transform " + genexp + " with call to '" + target.getName() + "' to " + target.getName() + " comprehension");
    }

    private static void redirectLocalRead(FrameSlot orig, FrameSlot target, PNode root) {
        for (ReadLocalVariableNode read : NodeUtil.findAllNodeInstances(root, ReadLocalVariableNode.class)) {
            if (read.getSlot().equals(orig)) {
                read.replace(ReadLocalVariableNode.create(target));
            }
        }
    }

    private static void redirectLocalWrite(FrameSlot orig, FrameSlot target, PNode root) {
        for (WriteLocalVariableNode write : NodeUtil.findAllNodeInstances(root, WriteLocalVariableNode.class)) {
            if (write.getSlot().equals(orig)) {
                write.replace(WriteLocalVariableNodeFactory.create(target, write.getRhs()));
            }
        }
    }

    private static void redirectLevelRead(PNode root) {
        for (ReadLevelVariableNode read : NodeUtil.findAllNodeInstances(root, ReadLevelVariableNode.class)) {
            read.replace(ReadLocalVariableNode.create(read.getSlot()));
        }
    }

}
