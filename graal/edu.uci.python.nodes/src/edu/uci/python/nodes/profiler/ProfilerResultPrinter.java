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
package edu.uci.python.nodes.profiler;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import com.oracle.truffle.api.nodes.*;

import edu.uci.python.nodes.*;
import edu.uci.python.nodes.control.*;
import edu.uci.python.nodes.function.*;
import edu.uci.python.runtime.*;

/**
 * @author Gulfem
 */

public class ProfilerResultPrinter {

    private PrintStream out = System.out;

    private final PythonProfilerNodeProber profilerProber;

    private List<PNode> nodesEmptySourceSections = new ArrayList<>();

    private List<PNode> nodesUsingExistingProbes = new ArrayList<>();

    public ProfilerResultPrinter(PythonProfilerNodeProber profilerProber) {
        this.profilerProber = profilerProber;
    }

    public void addNodeEmptySourceSection(PNode node) {
        nodesEmptySourceSections.add(node);
    }

    public void addNodeUsingExistingProbe(PNode node) {
        nodesUsingExistingProbes.add(node);
    }

    public void printCallProfilerResults() {
        Map<PythonWrapperNode, ProfilerInstrument> calls;
        if (PythonOptions.SortProfilerResults) {
            calls = sortByValue(profilerProber.getCallWrapperToInstruments());
        } else {
            calls = profilerProber.getCallWrapperToInstruments();
        }

        if (calls.size() > 0) {
            printBanner("Call Profiling Results", 72);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-50s", "Function Name");
            out.format("%-20s", "Number of Calls");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-11s", "Length");
            out.println();
            out.println("===============                                   ===============     ====     ======     ======");

            Iterator<Map.Entry<PythonWrapperNode, ProfilerInstrument>> it = calls.entrySet().iterator();
            while (it.hasNext()) {
                Entry<PythonWrapperNode, ProfilerInstrument> entry = it.next();
                PythonWrapperNode wrapper = entry.getKey();
                ProfilerInstrument instrument = entry.getValue();
                PNode child = wrapper.getChild();
                FunctionRootNode rootNode = (FunctionRootNode) (wrapper.getParent());
                if (instrument.getCounter() > 0) {
                    out.format("%-50s", rootNode.getFunctionName());
                    out.format("%15s", instrument.getCounter());
                    out.format("%9s", child.getSourceSection().getStartLine());
                    out.format("%11s", child.getSourceSection().getStartColumn());
                    out.format("%11s", child.getSourceSection().getCharLength());
                    out.println();
                }
            }
        }
    }

    public void printLoopProfilerResults() {
        Map<PythonWrapperNode, ProfilerInstrument> loopBodies;
        if (PythonOptions.SortProfilerResults) {
            loopBodies = sortByValue(profilerProber.getLoopBodyWrapperToInstruments());
        } else {
            loopBodies = profilerProber.getLoopBodyWrapperToInstruments();
        }

        if (loopBodies.size() > 0) {
            printBanner("Loop Profiling Results", 72);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-50s", "Node");
            out.format("%-20s", "Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-11s", "Length");
            out.println();
            out.println("=============                                     ===============     ====     ======     ======");

            Iterator<Map.Entry<PythonWrapperNode, ProfilerInstrument>> it = loopBodies.entrySet().iterator();
            while (it.hasNext()) {
                Entry<PythonWrapperNode, ProfilerInstrument> entry = it.next();
                PythonWrapperNode wrapper = entry.getKey();
                Node loopNode = wrapper.getParent();
                ProfilerInstrument instrument = entry.getValue();
                if (instrument.getCounter() > 0) {
                    out.format("%-50s", loopNode.getClass().getSimpleName());
                    out.format("%15s", instrument.getCounter());
                    out.format("%9s", loopNode.getSourceSection().getStartLine());
                    out.format("%11s", loopNode.getSourceSection().getStartColumn());
                    out.format("%11s", loopNode.getSourceSection().getCharLength());
                    out.println();
                }
            }
        }
    }

    public void printIfProfilerResults() {
        Map<PythonWrapperNode, ProfilerInstrument> ifNodes;
        if (PythonOptions.SortProfilerResults) {
            ifNodes = sortByValue(profilerProber.getIfWrapperToInstruments());
        } else {
            ifNodes = profilerProber.getIfWrapperToInstruments();
        }

        if (ifNodes.size() > 0) {
            printBanner("If Node Profiling Results", 60);
            Map<PythonWrapperNode, ProfilerInstrument> thens = profilerProber.getThenWrapperToInstruments();
            Map<PythonWrapperNode, ProfilerInstrument> elses = profilerProber.getElseWrapperToInstruments();

            out.format("%-20s", "If Counter");
            out.format("%15s", "Then Counter");
            out.format("%20s", "Else Counter");
            out.format("%9s", "Line");
            out.format("%11s", "Column");
            out.format("%11s", "Length");
            out.println();
            out.println("===========            ============        ============     ====     ======     ======");

            Iterator<Map.Entry<PythonWrapperNode, ProfilerInstrument>> it = ifNodes.entrySet().iterator();
            while (it.hasNext()) {
                Entry<PythonWrapperNode, ProfilerInstrument> entry = it.next();
                PythonWrapperNode ifWrapper = entry.getKey();
                IfNode ifNode = (IfNode) ifWrapper.getChild();
                ProfilerInstrument ifInstrument = entry.getValue();
                PNode thenNode = ifNode.getThen();
                PNode elseNode = ifNode.getElse();
                ProfilerInstrument thenInstrument = thens.get(thenNode);

                if (ifInstrument.getCounter() > 0) {
                    out.format("%11s", ifInstrument.getCounter());
                    out.format("%24s", thenInstrument.getCounter());

                    if (!(ifNode.getElse() instanceof EmptyNode)) {
                        ProfilerInstrument elseInstrument = elses.get(elseNode);
                        out.format("%20s", elseInstrument.getCounter());
                    } else {
                        out.format("%20s", "-");
                    }

                    out.format("%9s", ifNode.getSourceSection().getStartLine());
                    out.format("%11s", ifNode.getSourceSection().getStartColumn());
                    out.format("%11s", ifNode.getSourceSection().getCharLength());
                    out.println();
                }
            }
        }
    }

    public void printNodeProfilerResults() {
        Map<PythonWrapperNode, ProfilerInstrument> nodes;
        if (PythonOptions.SortProfilerResults) {
            nodes = sortByValue(profilerProber.getWrapperToInstruments());
        } else {
            nodes = profilerProber.getWrapperToInstruments();
        }

        if (nodes.size() > 0) {
            printBanner("Node Profiling Results", 72);
            /**
             * 50 is the length of the text by default padding left padding is added, so space is
             * added to the beginning of the string, minus sign adds padding to the right
             */

            out.format("%-50s", "Node");
            out.format("%-20s", "Counter");
            out.format("%-9s", "Line");
            out.format("%-11s", "Column");
            out.format("%-11s", "Length");
            out.println();
            out.println("=============                                     ===============     ====     ======     ======");

            Iterator<Map.Entry<PythonWrapperNode, ProfilerInstrument>> it = nodes.entrySet().iterator();
            while (it.hasNext()) {
                Entry<PythonWrapperNode, ProfilerInstrument> entry = it.next();
                PythonWrapperNode wrapper = entry.getKey();
                ProfilerInstrument instrument = entry.getValue();
                if (instrument.getCounter() > 0) {
                    PNode child = wrapper.getChild();
                    out.format("%-50s", child.getClass().getSimpleName());
                    out.format("%15s", instrument.getCounter());
                    out.format("%9s", child.getSourceSection().getStartLine());
                    out.format("%11s", child.getSourceSection().getStartColumn());
                    out.format("%11s", child.getSourceSection().getCharLength());
                    out.println();
                }
            }
        }
    }

    private static Map<PythonWrapperNode, ProfilerInstrument> sortByValue(Map<PythonWrapperNode, ProfilerInstrument> map) {
        List<Map.Entry<PythonWrapperNode, ProfilerInstrument>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<PythonWrapperNode, ProfilerInstrument>>() {

            public int compare(Map.Entry<PythonWrapperNode, ProfilerInstrument> m1, Map.Entry<PythonWrapperNode, ProfilerInstrument> m2) {
                return Long.compare(m2.getValue().getCounter(), m1.getValue().getCounter());
            }
        });

        Map<PythonWrapperNode, ProfilerInstrument> result = new LinkedHashMap<>();
        for (Map.Entry<PythonWrapperNode, ProfilerInstrument> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    public void printNodesEmptySourceSections() {
        if (nodesEmptySourceSections.size() > 0) {
            printBanner("Nodes That Have Empty Source Sections", 10);
            for (PNode node : nodesEmptySourceSections) {
                out.println(node.getClass().getSimpleName() + " in " + node.getRootNode());
            }
        }
    }

    public void printNodesUsingExistingProbes() {
        if (nodesUsingExistingProbes.size() > 0) {
            printBanner("Nodes That Reuses an Existing Probe", 10);
            for (PNode node : nodesUsingExistingProbes) {
                out.println(node.getClass().getSimpleName() + " in " + node.getRootNode());
            }
        }
    }

    private static void printBanner(String caption, int size) {
        // CheckStyle: stop system..print check
        for (int i = 0; i < size / 2; i++) {
            System.out.print("=");
        }

        System.out.print(" " + caption + " ");

        for (int i = 0; i < size / 2; i++) {
            System.out.print("=");
        }

        System.out.println();
        // CheckStyle: resume system..print check
    }
}
