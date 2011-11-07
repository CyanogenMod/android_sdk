/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.lint.detector.api;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * A detector is able to find a particular problem. It might also be thought of as enforcing
 * a rule, but "rule" is a bit overloaded in ADT terminology since ViewRules are used in
 * the Rules API to allow views to specify designtime behavior in the graphical layout editor.
 * <p>
 * Each detector provides information about the issues it can find, such as an explanation
 * of how to fix the issue, the priority, the category, etc. It also has an id which is
 * used to persistently identify a particular type of error.
 * <p/>
 * NOTE: Detectors might be constructed just once and shared between lint runs, so
 * any per-detector state should be initialized and reset via the before/after
 * methods.
 * <p/>
 * <b>NOTE: This is not a public or final API; if you rely on this be prepared
 * to adjust your code for the next tools release.</b>
 */
public abstract class Detector {
    /** Specialized interface for detectors that scan Java source files */
    public interface JavaScanner  {
        void checkJavaSources(Context context, List<File> sourceFolders);
    }

    /** Specialized interface for detectors that scan Java class files */
    public interface ClassScanner  {
        void checkJavaClasses(Context context);
    }

    /** Specialized interface for detectors that scan XML files */
    public interface XmlScanner {
        /**
         * Visit the given document. The detector is responsible for its own iteration
         * through the document.
         * @param context information about the document being analyzed
         * @param document the document to examine
         */
        void visitDocument(Context context, Document document);

        /**
         * Visit the given element.
         * @param context information about the document being analyzed
         * @param element the element to examine
         */
        void visitElement(Context context, Element element);

        /**
         * Visit the given element after its children have been analyzed.
         * @param context information about the document being analyzed
         * @param element the element to examine
         */
        void visitElementAfter(Context context, Element element);

        /**
         * Visit the given attribute.
         * @param context information about the document being analyzed
         * @param attribute the attribute node to examine
         */
        void visitAttribute(Context context, Attr attribute);

        /**
         * Returns the list of elements that this detector wants to analyze. If non
         * null, this detector will be called (specifically, the
         * {@link #visitElement} method) for each matching element in the document.
         * <p>
         * If this method returns null, and {@link #getApplicableAttributes()} also returns
         * null, then the {@link #visitDocument} method will be called instead.
         *
         * @return a collection of elements, or null, or the special
         *         {@link XmlScanner#ALL} marker to indicate that every single
         *         element should be analyzed.
         */
        Collection<String> getApplicableElements();

        /**
         * Returns the list of attributes that this detector wants to analyze. If non
         * null, this detector will be called (specifically, the
         * {@link #visitAttribute} method) for each matching attribute in the document.
         * <p>
         * If this method returns null, and {@link #getApplicableElements()} also returns
         * null, then the {@link #visitDocument} method will be called instead.
         *
         * @return a collection of attributes, or null, or the special
         *         {@link XmlScanner#ALL} marker to indicate that every single
         *         attribute should be analyzed.
         */
        Collection<String> getApplicableAttributes();

        /**
         * Special marker collection returned by {@link #getApplicableElements()} or
         * {@link #getApplicableAttributes()} to indicate that the check should be
         * invoked on all elements or all attributes
         */
        public static final List<String> ALL = new ArrayList<String>(0);
    }

    /** Concrete implementation of a detector that is a {@link Detector.XmlScanner} */
    public static abstract class XmlDetectorAdapter extends Detector
            implements Detector.XmlScanner {

        @Override
        public void run(Context context) {
        }

        @Override
        public boolean appliesTo(Context context, File file) {
            return false;
        }

        public void visitDocument(Context context, Document document) {
            // This method must be overridden if your detector does
            // not return something from getApplicableElements or
            // getApplicableATtributes
            assert false;
        }

        public void visitElement(Context context, Element element) {
            // This method must be overridden if your detector returns
            // tag names from getApplicableElements
            assert false;
        }

        public void visitElementAfter(Context context, Element element) {
        }

        public void visitAttribute(Context context, Attr attribute) {
            // This method must be overridden if your detector returns
            // attribute names from getApplicableAttributes
            assert false;
        }

        public Collection<String> getApplicableElements() {
            return null;
        }

        public Collection<String> getApplicableAttributes() {
            return null;
        }
    }

    /**
     * Runs the detector
     *
     * @param context the context describing the work to be done
     */
    public abstract void run(Context context);

    /** Returns true if this detector applies to the given file */
    public abstract boolean appliesTo(Context context, File file);

    /**
     * Analysis is about to begin, perform any setup steps.
     * <p>
     * TODO: Rename "check" to "scan" here? beforeScanProject, beforeScanFile
     * etc?
     */
    public void beforeCheckProject(Context context) {
    }

    /**
     * Analysis has just been finished for the whole project, perform any
     * cleanup or report issues found
     */
    public void afterCheckProject(Context context) {
    }

    /** Analysis is about to be performed on a specific file, perform any setup steps. */
    public void beforeCheckFile(Context context) {
    }

    /**
     * Analysis has just been finished for a specific file, perform any cleanup
     * or report issues found
     */
    public void afterCheckFile(Context context) {
    }

    /**
     * Returns the expected speed of this detector
     *
     * @return the expected speed of this detector
     */
    public abstract Speed getSpeed();
}
