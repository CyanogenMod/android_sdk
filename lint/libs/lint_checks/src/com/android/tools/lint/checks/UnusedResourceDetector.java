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

package com.android.tools.lint.checks;

import static com.android.tools.lint.checks.LintConstants.ANDROID_MANIFEST_XML;

import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.android.tools.lint.detector.api.Context;
import com.android.tools.lint.detector.api.Detector;
import com.android.tools.lint.detector.api.Issue;
import com.android.tools.lint.detector.api.Location;
import com.android.tools.lint.detector.api.Position;
import com.android.tools.lint.detector.api.ResourceXmlDetector;
import com.android.tools.lint.detector.api.Scope;
import com.android.tools.lint.detector.api.Severity;
import com.android.tools.lint.detector.api.Speed;

import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds unused resources.
 * <p>
 * Note: This detector currently performs *string* analysis to check Java files.
 * The Lint API needs an official Java AST API (or map to an existing one like
 * BCEL for bytecode analysis etc) and once it does this should be updated to
 * use it.
 */
public class UnusedResourceDetector extends ResourceXmlDetector implements Detector.JavaScanner {
    private static final String R_PREFIX = "R."; //$NON-NLS-1$

    /** Unused resources (other than ids). */
    public static final Issue ISSUE = Issue.create("UnusedResources", //$NON-NLS-1$
            "Looks for unused resources",
            "Unused resources make applications larger and slow down builds.",
            CATEGORY_PERFORMANCE, 3, Severity.WARNING, Scope.PROJECT);
    /** Unused id's */
    public static final Issue ISSUE_IDS = Issue.create("UnusedIds", //$NON-NLS-1$
            "Looks for unused id's",
            "This resource id definition appears not to be needed since it is not referenced " +
            "from anywhere. Having id definitions, even if unused, is not necessarily a bad " +
            "idea since they make working on layouts and menus easier, so there is not a " +
            "strong reason to delete these.",
            CATEGORY_PERFORMANCE, 1, Severity.WARNING, Scope.PROJECT).setEnabledByDefault(false);

    protected Set<String> mDeclarations;
    protected Set<String> mReferences;
    protected Map<String, Attr> mIdToAttr;
    protected Map<Attr, File> mAttrToFile;
    protected Map<Attr, Location> mAttrToLocation;

    /**
     * Constructs a new {@link UnusedResourceDetector}
     */
    public UnusedResourceDetector() {
    }

    @Override
    public Issue[] getIssues() {
        return new Issue[] { ISSUE };
    }

    @Override
    public void run(Context context) {
        assert false;
    }

    @Override
    public void beforeCheckProject(Context context) {
        mIdToAttr = new HashMap<String, Attr>(300);
        mAttrToFile = new HashMap<Attr, File>(300);
        mAttrToLocation = new HashMap<Attr, Location>(300);
        mDeclarations = new HashSet<String>(300);
        mReferences = new HashSet<String>(300);
    }

    // ---- Implements JavaScanner ----

    public void checkJavaSources(Context context, List<File> sourceFolders) {
        // TODO: Use a proper Java AST...
        // For right now, this is hacked via String scanning in .java files instead.
        // TODO: Look up from project metadata
        File src = new File(context.projectDir, "src"); //$NON-NLS-1$
        if (!src.exists()) {
            context.toolContext.log(null, "Did not find source folder in project");
        } else {
            scanJavaFile(context, src);
        }

        File gen = new File(context.projectDir, "gen"); //$NON-NLS-1$
        if (!gen.exists()) {
            context.toolContext.log(null, "Did not find gen folder in project");
        } else {
            scanJavaFile(context, gen);
        }
    }

    private void scanJavaFile(Context context, File file) {
        String fileName = file.getName();
        if (fileName.endsWith(".java") && file.exists()) { //$NON-NLS-1$
            if (fileName.equals("R.java")) { //$NON-NLS-1$
                addJavaDeclarations(context, file);
            } else {
                addJavaReferences(context, file);
            }
        } else if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    scanJavaFile(context, child);
                }
            }
        }
    }

    private static final String CLASS_DECLARATION = "public static final class "; //$NON-NLS-1$
    private static final String FIELD_CONST_DECLARATION = "public static final int "; //$NON-NLS-1$
    private static final String FIELD_DECLARATION = "public static int "; //$NON-NLS-1$

    private void addJavaDeclarations(Context context, File file) {
        // mDeclarations
        String s = context.toolContext.readFile(file);
        String[] lines = s.split("\n"); //$NON-NLS-1$
        String currentType = null;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (int j = 0; j < line.length(); j++) {
                char c = line.charAt(j);
                if (!Character.isWhitespace(c)) {
                    // Found beginning of line
                    boolean startsWithConstField = line.startsWith(FIELD_CONST_DECLARATION, j);
                    boolean startsWithField = line.startsWith(FIELD_DECLARATION, j);
                    if (startsWithConstField || startsWithField) {
                        // Field (constant
                        int nameBegin = j + (startsWithField
                                ? FIELD_DECLARATION.length() : FIELD_CONST_DECLARATION.length());
                        int nameEnd = line.indexOf('=', nameBegin);
                        assert currentType != null;
                        if (nameEnd != -1 && currentType != null) {
                            String name = line.substring(nameBegin, nameEnd);
                            String r = R_PREFIX + currentType + '.' + name;
                            mDeclarations.add(r);
                        }
                    } else if (line.startsWith(CLASS_DECLARATION, j)) {
                        // New class
                        int typeBegin = j + CLASS_DECLARATION.length();
                        int typeEnd = line.indexOf(' ', typeBegin);
                        if (typeEnd != -1) {
                            currentType = line.substring(typeBegin, typeEnd);
                        }
                    }
                }
            }
        }
    }

    /** Adds the resource identifiers found in the given file into the given set */
    private void addJavaReferences(Context context, File file) {
        String s = context.toolContext.readFile(file);
        if (s.length() <= 2) {
            return;
        }

        // Scan looking for R.{type}.name identifiers
        // Extremely simple state machine which just avoids comments, line comments
        // and strings, and outside of that records any R. identifiers it finds
        int index = 0;
        int length = s.length();

        char c = s.charAt(0);
        char next = s.charAt(1);
        for (; index < length - 2; index++, c = s.charAt(index), next = s.charAt(index + 1)) {
            if (Character.isWhitespace(c)) {
                continue;
            }
            if (c == '/') {
                if (next == '*') {
                    // Block comment
                    while (index < length - 2) {
                        if (s.charAt(index) == '*' && s.charAt(index + 1) == '/') {
                            break;
                        }
                        index++;
                    }
                    index++;
                } else if (next == '/') {
                    // Line comment
                    while (index < length && s.charAt(index) != '\n') {
                        index++;
                    }
                }
            } else if (c == '\'') {
                // Character
                if (next == '\\') {
                    // Skip '\c'
                    index += 2;
                } else {
                    // Skip 'c'
                    index++;
                }
            } else if (c == '\"') {
                // String: Skip to end
                index++;
                while (index < length - 1) {
                    char t = s.charAt(index);
                    if (t == '\\') {
                        index++;
                    } else if (t == '"') {
                        break;
                    }
                    index++;
                }
            } else if (c == 'R' && next == '.') {
                // This might be a pattern
                int begin = index;
                index += 2;
                while (index < length) {
                    char t = s.charAt(index);
                    if (t == '.') {
                        String typeName = s.substring(begin + 2, index);
                        ResourceType type = ResourceType.getEnum(typeName);
                        if (type != null) {
                            index++;
                            begin = index;
                            while (index < length &&
                                    Character.isJavaIdentifierPart(s.charAt(index))) {
                                index++;
                            }
                            if (index > begin) {
                                String name = R_PREFIX + typeName + '.'
                                        + s.substring(begin, index);
                                mReferences.add(name);
                            }
                        }
                        index--;
                        break;
                    } else if (!Character.isJavaIdentifierStart(t)) {
                        break;
                    }
                    index++;
                }
            } else if (Character.isJavaIdentifierPart(c)) {
                // Skip to the end of the identifier
                while (index < length && Character.isJavaIdentifierPart(s.charAt(index))) {
                    index++;
                }
                // Back up so the next character can be checked to see if it's a " etc
                index--;
            } else {
                // Just punctuation/operators ( ) ;  etc
            }
        }
    }

    private void addManifestReferences(Context context) {
        File manifestFile = new File(context.projectDir, ANDROID_MANIFEST_XML);
        if (manifestFile.exists()) {
            Context ctx = new Context(context.toolContext, context.projectDir, manifestFile,
                    context.scope);
            Document document = context.toolContext.getParser().parse(ctx);
            addManifestReferences(context, document.getDocumentElement());
        }
    }

    private void addManifestReferences(Context context, Element element) {
        NamedNodeMap attributes = element.getAttributes();
        for (int i = 0, n = attributes.getLength(); i < n; i++) {
            Attr attribute = (Attr) attributes.item(i);
            visitAttribute(context, attribute);
        }

        NodeList childNodes = element.getChildNodes();
        for (int i = 0, n = childNodes.getLength(); i < n; i++) {
            Node child = childNodes.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                addManifestReferences(context, (Element) child);
            }
        }
    }

    @Override
    public void afterCheckProject(Context context) {
        addManifestReferences(context);

        mDeclarations.removeAll(mReferences);
        Set<String> unused = mDeclarations;

        if (unused.size() > 0 && !context.toolContext.isEnabled(ISSUE_IDS)) {
            // Remove all R.id references
            List<String> ids = new ArrayList<String>();
            for (String resource : unused) {
                if (resource.startsWith("R.id.")) { //$NON-NLS-1$
                    ids.add(resource);
                }
            }
            unused.removeAll(ids);
        }

        List<String> sorted = new ArrayList<String>();
        for (String r : unused) {
            sorted.add(r);
        }
        Collections.sort(sorted);

        for (String resource : sorted) {
            String message = String.format("The resource %1$s appears to be unused", resource);
            Location location = null;
            Attr attr = mIdToAttr.get(resource);
            if (attr != null) {
                location = mAttrToLocation.get(attr);
                if (location == null) {
                    File f = mAttrToFile.get(attr);
                    Position start = context.toolContext.getParser().getStartPosition(context,
                            attr);
                    Position end = null;
                    if (start != null) {
                        end = context.toolContext.getParser().getEndPosition(context, attr);
                    }
                    location = new Location(f, start, end);
                }
            } else {
                // Try to figure out the file if it's a file based resource (such as R.layout) --
                // in that case we can figure out the filename since it has a simple mapping
                // from the resource name (though the presence of qualifiers like -land etc
                // makes it a little tricky if there's no base file provided)
                int secondDot = resource.indexOf('.', 2);
                String typeName = resource.substring(2, secondDot); // 2: Skip R.
                ResourceType type = ResourceType.getEnum(typeName);
                if (type != null && isFileBasedResourceType(type)) {
                    String name = resource.substring(secondDot + 1);
                    File file = new File(context.projectDir,
                            "res" + File.separator + typeName + File.separator + //$NON-NLS-1$
                            name + ".xml"); //$NON-NLS-1$
                    if (file.exists()) {
                        location = new Location(file, null, null);
                    }
                }
            }
            context.toolContext.report(context, ISSUE, location, message, resource);
        }

        mReferences = null;
        mAttrToFile = null;
        mAttrToLocation = null;
        mIdToAttr = null;
        mDeclarations = null;
    }

    /** Determine if the given type corresponds to a resource that has a unique file */
    private static boolean isFileBasedResourceType(ResourceType type) {
        List<ResourceFolderType> folderTypes = FolderTypeRelationship.getRelatedFolders(type);
        for (ResourceFolderType folderType : folderTypes) {
            if (folderType != ResourceFolderType.VALUES) {
                if (type == ResourceType.ID) {
                    return false;
                }
                return true;
            }
        }
        return false;
    }


    @Override
    public Collection<String> getApplicableAttributes() {
        return ALL;
    }

    @Override
    public void visitAttribute(Context context, Attr attribute) {
        String value = attribute.getValue();
        if (value.startsWith("@+")) { //$NON-NLS-1$
            String r = R_PREFIX + value.substring(2).replace('/', '.');
            // We already have the declarations when we scan the R file, but we're tracking
            // these here to get attributes for position info
            mDeclarations.add(r);
            mIdToAttr.put(r, attribute);
            mAttrToFile.put(attribute, context.file);
            // It's important for this to be lightweight since we're storing ALL attribute
            // locations even if we don't know that we're going to have any unused resources!
            mAttrToLocation.put(attribute, context.parser.getLocation(context, attribute));
        } else if (value.startsWith("@")              //$NON-NLS-1$
                && !value.startsWith("@android:")) {  //$NON-NLS-1$
            // Compute R-string, e.g. @string/foo => R.string.foo
            String r = R_PREFIX + value.substring(1).replace('/', '.');
            mReferences.add(r);
        }
    }

    @Override
    public Speed getSpeed() {
        return Speed.SLOW;
    }
}
