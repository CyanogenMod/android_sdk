/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.ide.eclipse.adt.internal.wizards.templates;

import static com.android.ide.eclipse.adt.AdtConstants.DOT_FTL;
import static com.android.ide.eclipse.adt.AdtConstants.DOT_XML;
import static com.android.sdklib.SdkConstants.FD_EXTRAS;
import static com.android.sdklib.SdkConstants.FD_TEMPLATES;
import static com.android.sdklib.SdkConstants.FD_TOOLS;

import com.android.annotations.NonNull;
import com.android.annotations.Nullable;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.formatting.XmlFormatPreferences;
import com.android.ide.eclipse.adt.internal.editors.formatting.XmlFormatStyle;
import com.android.ide.eclipse.adt.internal.editors.formatting.XmlPrettyPrinter;
import com.android.ide.eclipse.adt.internal.editors.layout.gle2.DomUtilities;
import com.android.ide.eclipse.adt.internal.preferences.AdtPrefs;
import com.android.manifmerger.ManifestMerger;
import com.android.manifmerger.MergerLog;
import com.android.resources.ResourceFolderType;
import com.android.sdklib.SdkConstants;
import com.google.common.base.Charsets;
import com.google.common.io.Files;

import freemarker.cache.TemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.DefaultObjectWrapper;
import freemarker.template.Template;
import freemarker.template.TemplateException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import lombok.ast.libs.org.parboiled.google.collect.Lists;

/**
 * Handler which manages instantiating FreeMarker templates, copying resources
 * and merging into existing files
 */
class TemplateHandler {
    /** Highest supported format; templates with a higher number will be skipped */
    static final int CURRENT_FORMAT = 1;

    /**
     * Special marker indicating that this path refers to the special shared
     * resource directory rather than being somewhere inside the root/ directory
     * where all template specific resources are found
     */
    private static final String VALUE_TEMPLATE_DIR = "$TEMPLATEDIR"; //$NON-NLS-1$

    /**
     * Directory within the template which contains the resources referenced
     * from the template.xml file
     */
    private static final String DATA_ROOT = "root";      //$NON-NLS-1$

    /**
     * Shared resource directory containing common resources shared among
     * multiple templates
     */
    private static final String RESOURCE_ROOT = "resources";   //$NON-NLS-1$

    /** Reserved filename which describes each template */
    static final String TEMPLATE_XML = "template.xml";   //$NON-NLS-1$

    // Various tags and attributes used in the template metadata files - template.xml,
    // globals.xml.ftl, recipe.xml.ftl, etc.

    static final String TAG_MERGE = "merge";             //$NON-NLS-1$
    static final String TAG_EXECUTE = "execute";         //$NON-NLS-1$
    static final String TAG_GLOBALS = "globals";         //$NON-NLS-1$
    static final String TAG_GLOBAL = "global";           //$NON-NLS-1$
    static final String TAG_PARAMETER = "parameter";     //$NON-NLS-1$
    static final String TAG_COPY = "copy";               //$NON-NLS-1$
    static final String TAG_INSTANTIATE = "instantiate"; //$NON-NLS-1$
    static final String TAG_OPEN = "open";               //$NON-NLS-1$
    static final String TAG_THUMB = "thumb";             //$NON-NLS-1$
    static final String TAG_THUMBS = "thumbs";           //$NON-NLS-1$
    static final String TAG_DEPENDENCY = "dependency";   //$NON-NLS-1$
    static final String ATTR_FORMAT = "format";          //$NON-NLS-1$
    static final String ATTR_REVISION = "revision";      //$NON-NLS-1$
    static final String ATTR_VALUE = "value";            //$NON-NLS-1$
    static final String ATTR_DEFAULT = "default";        //$NON-NLS-1$
    static final String ATTR_SUGGEST = "suggest";        //$NON-NLS-1$
    static final String ATTR_ID = "id";                  //$NON-NLS-1$
    static final String ATTR_NAME = "name";              //$NON-NLS-1$
    static final String ATTR_DESCRIPTION = "description";//$NON-NLS-1$
    static final String ATTR_TYPE = "type";              //$NON-NLS-1$
    static final String ATTR_HELP = "help";              //$NON-NLS-1$
    static final String ATTR_FILE = "file";              //$NON-NLS-1$
    static final String ATTR_TO = "to";                  //$NON-NLS-1$
    static final String ATTR_FROM = "from";              //$NON-NLS-1$
    static final String ATTR_CONSTRAINTS = "constraints";//$NON-NLS-1$

    static final String CATEGORY_ACTIVITIES = "activities";//$NON-NLS-1$
    static final String CATEGORY_PROJECTS = "projects";    //$NON-NLS-1$
    static final String CATEGORY_OTHER = "other";          //$NON-NLS-1$


    /** Default padding to apply in wizards around the thumbnail preview images */
    static final int PREVIEW_PADDING = 10;

    /** Default width to scale thumbnail preview images in wizards to */
    static final int PREVIEW_WIDTH = 200;

    /**
     * List of files to open after the wizard has been created (these are
     * identified by {@link #TAG_OPEN} elements in the recipe file
     */
    private final List<String> mOpen = Lists.newArrayList();

    /** Path to the directory containing the templates */
    @NonNull
    private final File mRootPath;

    /** The template loader which is responsible for finding (and sharing) template files */
    private final MyTemplateLoader mLoader;

    /** Agree to all file-overwrites from now on? */
    private boolean mYesToAll = false;

    /** Is writing the template cancelled? */
    private boolean mNoToAll = false;

    /**
     * Should files that we merge contents into be backed up? If yes, will
     * create emacs-style tilde-file backups (filename.xml~)
     */
    private boolean mBackupMergedFiles = true;

    /**
     * Template metadata
     */
    private TemplateMetadata mTemplate;

    /** Creates a new {@link TemplateHandler} for the given root path */
    static TemplateHandler createFromPath(File rootPath) {
        return new TemplateHandler(rootPath);
    }

    /** Creates a new {@link TemplateHandler} for the template name, which should
     * be relative to the templates directory */
    static TemplateHandler createFromName(String relative) {
        return new TemplateHandler(new File(getTemplateRootFolder(), relative));
    }

    private TemplateHandler(File rootPath) {
        mRootPath = rootPath;
        mLoader = new MyTemplateLoader();
        mLoader.setPrefix(mRootPath.getPath());
    }

    public void setBackupMergedFiles(boolean backupMergedFiles) {
        mBackupMergedFiles = backupMergedFiles;
    }

    public void render(final File outputPath, Map<String, Object> args) {
        if (!outputPath.exists()) {
            outputPath.mkdirs();
        }

        // Render the instruction list template.
        Map<String, Object> paramMap = createParameterMap(args);
        Configuration freemarker = new Configuration();
        freemarker.setObjectWrapper(new DefaultObjectWrapper());
        freemarker.setTemplateLoader(mLoader);

        processVariables(freemarker, TEMPLATE_XML, paramMap, outputPath);
    }

    Map<String, Object> createParameterMap(Map<String, Object> args) {
        final Map<String, Object> paramMap = createBuiltinMap();

        // Wizard parameters supplied by user, specific to this template
        paramMap.putAll(args);

        return paramMap;
    }

    /** Data model for the templates */
    static Map<String, Object> createBuiltinMap() {
        // Create the data model.
        final Map<String, Object> paramMap = new HashMap<String, Object>();

        // Builtin conversion methods
        paramMap.put("slashedPackageName", new FmSlashedPackageNameMethod());       //$NON-NLS-1$
        paramMap.put("camelCaseToUnderscore", new FmCamelCaseToUnderscoreMethod()); //$NON-NLS-1$
        paramMap.put("underscoreToCamelCase", new FmUnderscoreToCamelCaseMethod()); //$NON-NLS-1$
        paramMap.put("activityToLayout", new FmActivityToLayoutMethod());           //$NON-NLS-1$
        paramMap.put("layoutToActivity", new FmLayoutToActivityMethod());           //$NON-NLS-1$

        // This should be handled better: perhaps declared "required packages" as part of the
        // inputs? (It would be better if we could conditionally disable template based
        // on availability)
        Map<String, String> builtin = new HashMap<String, String>();
        builtin.put("templatesRes", VALUE_TEMPLATE_DIR); //$NON-NLS-1$
        paramMap.put("android", builtin);                //$NON-NLS-1$

        return paramMap;
    }

    @Nullable
    public TemplateMetadata getTemplate() {
        if (mTemplate == null) {
            String xml = readTemplateTextResource(TEMPLATE_XML);
            if (xml != null) {
                Document doc = DomUtilities.parseDocument(xml, true);
                if (doc != null && doc.getDocumentElement() != null) {
                    mTemplate = new TemplateMetadata(doc);
                }
            }
        }

        return mTemplate;
    }

    @Nullable
    public static TemplateMetadata getTemplate(String templateName) {
        String relative = templateName + '/' + TEMPLATE_XML;

        File templateFile = getTemplateLocation(relative);
        if (templateFile != null) {
            try {
                String xml = Files.toString(templateFile, Charsets.UTF_8);
                Document doc = DomUtilities.parseDocument(xml, true);
                if (doc != null && doc.getDocumentElement() != null) {
                    return new TemplateMetadata(doc);
                }
            } catch (IOException e) {
                AdtPlugin.log(e, null);
                return null;
            }
        }

        return null;
    }

    @NonNull
    public String getResourcePath(String templateName) {
        return new File(mRootPath.getPath(), templateName).getPath();
    }

    @Nullable
    public static File getTemplateRootFolder() {
        String location = AdtPrefs.getPrefs().getOsSdkFolder();
        if (location != null) {
            File folder = new File(location, FD_TOOLS + File.separator + FD_TEMPLATES);
            if (folder.isDirectory()) {
                return folder;
            }
        }

        return null;
    }

    @Nullable
    public static File getExtraTemplateRootFolder() {
        String location = AdtPrefs.getPrefs().getOsSdkFolder();
        if (location != null) {
            File folder = new File(location, FD_EXTRAS + File.separator + FD_TEMPLATES);
            if (folder.isDirectory()) {
                return folder;
            }
        }

        return null;
    }

    @Nullable
    public static File getTemplateLocation(@NonNull File root, @NonNull String relativePath) {
        File templateRoot = getTemplateRootFolder();
        if (templateRoot != null) {
            String rootPath = root.getPath();
            File templateFile = new File(templateRoot,
                    rootPath.replace('/', File.separatorChar) + File.separator
                    + relativePath.replace('/', File.separatorChar));
            if (templateFile.exists()) {
                return templateFile;
            }
        }

        return null;
    }

    @Nullable
    public static File getTemplateLocation(@NonNull String relativePath) {
        File templateRoot = getTemplateRootFolder();
        if (templateRoot != null) {
            File templateFile = new File(templateRoot,
                    relativePath.replace('/', File.separatorChar));
            if (templateFile.exists()) {
                return templateFile;
            }
        }

        return null;

    }

    /**
     * Load a text resource for the given relative path within the template
     *
     * @param relativePath relative path within the template
     * @return the string contents of the template text file
     */
    @Nullable
    public String readTemplateTextResource(@NonNull String relativePath) {
        try {
            return Files.toString(new File(mRootPath,
                    relativePath.replace('/', File.separatorChar)), Charsets.UTF_8);
        } catch (IOException e) {
            AdtPlugin.log(e, null);
            return null;
        }
    }

    @Nullable
    public String readTemplateTextResource(@NonNull File file) {
        assert file.isAbsolute();
        try {
            return Files.toString(file, Charsets.UTF_8);
        } catch (IOException e) {
            AdtPlugin.log(e, null);
            return null;
        }
    }

    /**
     * Reads the contents of a resource
     *
     * @param relativePath the path relative to the template directory
     * @return the binary data read from the file
     */
    @Nullable
    public byte[] readTemplateResource(@NonNull String relativePath) {
        try {
            return Files.toByteArray(new File(mRootPath, relativePath));
        } catch (IOException e) {
            AdtPlugin.log(e, null);
            return null;
        }
    }

    /** Read the given FreeMarker file and process the variable definitions */
    private void processVariables(final Configuration freemarker,
            String file, final Map<String, Object> paramMap, final File outputPath) {
        try {
            String xml;
            if (file.endsWith(DOT_XML)) {
                // Just read the file
                xml = readTemplateTextResource(file);
                if (xml == null) {
                    return;
                }
            } else {
                mLoader.setTemplateFile(new File(mRootPath, file));
                Template inputsTemplate = freemarker.getTemplate(file);
                StringWriter out = new StringWriter();
                inputsTemplate.process(paramMap, out);
                out.flush();
                xml = out.toString();
            }

            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            saxParser.parse(new ByteArrayInputStream(xml.getBytes()), new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String name,
                        Attributes attributes)
                        throws SAXException {
                    if (TAG_PARAMETER.equals(name)) {
                        String id = attributes.getValue(ATTR_ID);
                        if (!paramMap.containsKey(id)) {
                            String value = attributes.getValue(ATTR_DEFAULT);
                            paramMap.put(id, value);
                        }
                    } else if (TAG_GLOBAL.equals(name)) {
                        String id = attributes.getValue(ATTR_ID);
                        if (!paramMap.containsKey(id)) {
                            String value = attributes.getValue(ATTR_VALUE);
                            paramMap.put(id, value);
                        }
                    } else if (TAG_GLOBALS.equals(name)) {
                        // Handle evaluation of variables
                        String path = attributes.getValue(ATTR_FILE);
                        if (path != null) {
                            processVariables(freemarker, path, paramMap, outputPath);
                        } // else: <globals> root element
                    } else if (TAG_EXECUTE.equals(name)) {
                        String path = attributes.getValue(ATTR_FILE);
                        if (path != null) {
                            execute(freemarker, path, paramMap, outputPath);
                        }
                    } else if (!name.equals("template") && !name.equals("category")
                            && !name.equals("option") && !name.equals(TAG_THUMBS) &&
                            !name.equals(TAG_THUMB)) {
                        System.err.println("WARNING: Unknown template directive " + name);
                    }
                }
            });
        } catch (Exception e) {
            AdtPlugin.log(e, null);
        }
    }

    private boolean canOverwrite(File file) {
        if (file.exists() && !file.isDirectory()) {
            // Warn that the file already exists and ask the user what to do
            if (!mYesToAll) {
                MessageDialog dialog = new MessageDialog(null, "File Already Exists", null,
                        String.format(
                                "%1$s already exists.\nWould you like to replace it?",
                                file.getPath()),
                        MessageDialog.QUESTION, new String[] {
                                // Yes will be moved to the end because it's the default
                                "Yes", "No", "Cancel", "Yes to All"
                        }, 0);
                int result = dialog.open();
                switch (result) {
                    case 0:
                        // Yes
                        break;
                    case 3:
                        // Yes to all
                        mYesToAll = true;
                        break;
                    case 1:
                        // No
                        return false;
                    case SWT.DEFAULT:
                    case 2:
                        // Cancel
                        mNoToAll = true;
                        return false;
                }
            }

            if (mBackupMergedFiles) {
                return makeBackup(file);
            } else {
                return file.delete();
            }
        }

        return true;
    }

    /** Executes the given recipe file: copying, merging, instantiating, opening files etc */
    private void execute(
            final Configuration freemarker,
            String file,
            final Map<String, Object> paramMap,
            final File outputPath) {
        try {
            mLoader.setTemplateFile(new File(mRootPath, file));
            Template freemarkerTemplate = freemarker.getTemplate(file);

            StringWriter out = new StringWriter();
            freemarkerTemplate.process(paramMap, out);
            out.flush();
            String xml = out.toString();

            // Parse and execute the resulting instruction list.
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();

            saxParser.parse(new ByteArrayInputStream(xml.getBytes()),
                    new DefaultHandler() {
                @Override
                public void startElement(String uri, String localName, String name,
                        Attributes attributes)
                        throws SAXException {
                    if (mNoToAll) {
                        return;
                    }

                    try {
                        boolean instantiate = TAG_INSTANTIATE.equals(name);
                        if (TAG_COPY.equals(name) || instantiate) {
                            String fromPath = attributes.getValue(ATTR_FROM);
                            String toPath = attributes.getValue(ATTR_TO);
                            if (toPath == null || toPath.isEmpty()) {
                                toPath = attributes.getValue(ATTR_FROM);
                                toPath = AdtUtils.stripSuffix(toPath, DOT_FTL);
                            }
                            File to = new File(outputPath, toPath);
                            if (instantiate) {
                                instantiate(freemarker, paramMap, fromPath, to);
                            } else {
                                copyBundledResource(fromPath, to);
                            }
                        } else if (TAG_MERGE.equals(name)) {
                            String fromPath = attributes.getValue(ATTR_FROM);
                            String toPath = attributes.getValue(ATTR_TO);
                            if (toPath == null || toPath.isEmpty()) {
                                toPath = attributes.getValue(ATTR_FROM);
                                toPath = AdtUtils.stripSuffix(toPath, DOT_FTL);
                            }
                            // Resources in template.xml are located within root/
                            File to = new File(outputPath, toPath);
                            merge(freemarker, paramMap, fromPath, to);
                        } else if (name.equals(TAG_OPEN)) {
                            // The relative path here is within the output directory:
                            String relativePath = attributes.getValue(ATTR_FILE);
                            if (relativePath != null && !relativePath.isEmpty()) {
                                mOpen.add(relativePath);
                            }
                        } else if (!name.equals("recipe")) { //$NON-NLS-1$
                            System.err.println("WARNING: Unknown template directive " + name);
                        }
                    } catch (Exception e) {
                        AdtPlugin.log(e, null);
                    }
                }
            });

        } catch (Exception e) {
            AdtPlugin.log(e, null);
        }
    }

    @NonNull
    private File getFullPath(@NonNull String fromPath) {
        if (fromPath.startsWith(VALUE_TEMPLATE_DIR)) {
            return new File(getTemplateRootFolder(), RESOURCE_ROOT + File.separator
                    + fromPath.substring(VALUE_TEMPLATE_DIR.length() + 1).replace('/',
                            File.separatorChar));
        }
        return new File(mRootPath, DATA_ROOT + File.separator + fromPath);
    }

    private void merge(
            @NonNull final Configuration freemarker,
            @NonNull final Map<String, Object> paramMap,
            @NonNull String relativeFrom,
            @NonNull File to) throws IOException, TemplateException {
        if (!to.exists()) {
            // The target file doesn't exist: don't merge, just copy
            boolean instantiate = relativeFrom.endsWith(DOT_FTL);
            if (instantiate) {
                instantiate(freemarker, paramMap, relativeFrom, to);
            } else {
                copyBundledResource(relativeFrom, to);
            }
            return;
        }

        if (!to.getPath().endsWith(DOT_XML)) {
            throw new RuntimeException("Only XML files can be merged at this point: " + to);
        }

        String xml = null;
        File from = getFullPath(relativeFrom);
        if (relativeFrom.endsWith(DOT_FTL)) {
            // Perform template substitution of the template prior to merging
            mLoader.setTemplateFile(from);
            Template template = freemarker.getTemplate(from.getName());
            Writer out = new StringWriter();
            template.process(paramMap, out);
            out.flush();
            xml = out.toString();
        } else {
            xml = readTemplateTextResource(from);
            if (xml == null) {
                return;
            }
        }

        String currentXml = Files.toString(to, Charsets.UTF_8);
        Document currentManifest = DomUtilities.parseStructuredDocument(currentXml);
        Document fragment = DomUtilities.parseStructuredDocument(xml);

        XmlFormatStyle formatStyle = XmlFormatStyle.MANIFEST;
        boolean modified;
        boolean ok;
        if (to.getName().equals(SdkConstants.FN_ANDROID_MANIFEST_XML)) {
            modified = ok = mergeManifest(currentManifest, fragment);
        } else {
            // Merge plain XML files
            ResourceFolderType folderType =
                    ResourceFolderType.getFolderType(to.getParentFile().getName());
            if (folderType != null) {
                formatStyle = XmlFormatStyle.getForFolderType(folderType);
            } else {
                formatStyle = XmlFormatStyle.FILE;
            }

            modified = mergeResourceFile(currentManifest, fragment, folderType, paramMap);
            ok = true;
        }

        // Finally write out the merged file (formatting etc)
        if (ok) {
            if (modified) {
                XmlPrettyPrinter printer = new XmlPrettyPrinter(
                        XmlFormatPreferences.create(), formatStyle, null);
                StringBuilder sb = new StringBuilder(2 );
                printer.prettyPrint(-1, currentManifest, null, null, sb, false /*openTagOnly*/);
                String contents = sb.toString();
                writeString(to, contents, false);
            }
        } else {
            // Just insert into file along with comment, using the "standard" conflict
            // syntax that many tools and editors recognize.
            String sep = AdtUtils.getLineSeparator();
            String contents =
                    "<<<<<<< Original" + sep
                    + currentXml + sep
                    + "=======" + sep
                    + xml
                    + ">>>>>>> Added" + sep;
            writeString(to, contents, false);
        }
    }

    /**
     * Writes the given contents into the given file (unless that file already
     * contains the given contents), and if the file exists ask user whether
     * the file should be overwritten (unless the user has already answered "Yes to All"
     * or "Cancel" (no to all).
     */
    private void writeString(File destination, String contents, boolean confirmOverwrite)
            throws IOException {
        // First make sure that the files aren't identical, in which case we can do
        // nothing (and not involve user)
        if (!(destination.exists()
                && isIdentical(contents.getBytes(Charsets.UTF_8), destination))) {
            // And if the file does exist (and is now known to be different),
            // ask user whether it should be replaced (canOverwrite will also
            // return true if the file doesn't exist)
            if (confirmOverwrite) {
                if (!canOverwrite(destination)) {
                    return;
                }
            } else {
                if (destination.exists()) {
                    if (mBackupMergedFiles) {
                        makeBackup(destination);
                    } else {
                        destination.delete();
                    }
                }
            }
            Files.write(contents, destination, Charsets.UTF_8);
        }
    }

    /** Merges the given resource file contents into the given resource file
     * @param paramMap */
    private boolean mergeResourceFile(Document currentManifest, Document fragment,
            ResourceFolderType folderType, Map<String, Object> paramMap) {
        boolean modified = false;

        // For layouts for example, I want to *append* inside the root all the
        // contents of the new file.
        // But for resources for example, I want to combine elements which specify
        // the same name or id attribute.
        // For elements like manifest files we need to insert stuff at the right
        // location in a nested way (activities in the application element etc)
        // but that doesn't happen for the other file types.
        Element root = fragment.getDocumentElement();
        NodeList children = root.getChildNodes();
        List<Node> nodes = new ArrayList<Node>(children.getLength());
        for (int i = children.getLength() - 1; i >= 0; i--) {
            Node child = children.item(i);
            nodes.add(child);
            root.removeChild(child);
        }

        root = currentManifest.getDocumentElement();

        if (folderType == ResourceFolderType.VALUES) {
            // Try to merge items of the same name
            Map<String, Node> old = new HashMap<String, Node>();
            NodeList newSiblings = root.getChildNodes();
            for (int i = newSiblings.getLength() - 1; i >= 0; i--) {
                Node child = newSiblings.item(i);
                if (child.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) child;
                    String name = getResourceId(element);
                    if (name != null) {
                        old.put(name, element);
                    }
                }
            }

            for (Node node : nodes) {
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String name = getResourceId(element);
                    Node replace = name != null ? old.get(name) : null;
                    if (replace != null) {
                        // There is an existing item with the same id: just replace it
                        // ACTUALLY -- let's NOT change it.
                        // Let's say you've used the activity wizard once, and it
                        // emits some configuration parameter as a resource that
                        // it depends on, say "padding". Then the user goes and
                        // tweaks the padding to some other number.
                        // Now running the wizard a *second* time for some new activity,
                        // we should NOT go and set the value back to the template's
                        // default!
                        //root.replaceChild(node, replace);

                        // ... ON THE OTHER HAND... What if it's a parameter class
                        // (where the template rewrites a common attribute). Here it's
                        // really confusing if the new parameter is not set. This is
                        // really an error in the template, since we shouldn't have conflicts
                        // like that, but we need to do something to help track this down.
                        AdtPlugin.log(null,
                                "Warning: Ignoring name conflict in resource file for name %1$s",
                                name);
                    } else {
                        root.appendChild(node);
                        modified = true;
                    }
                }
            }
        } else {
            // In other file types, such as layouts, just append all the new content
            // at the end.
            for (Node node : nodes) {
                root.appendChild(node);
                modified = true;
            }
        }
        return modified;
    }

    /** Merges the given manifest fragment into the given manifest file */
    private boolean mergeManifest(Document currentManifest, Document fragment) {
        // TODO change MergerLog.wrapSdkLog by a custom IMergerLog that will create
        // and maintain error markers.
        ManifestMerger merger = new ManifestMerger(MergerLog.wrapSdkLog(AdtPlugin.getDefault()));
        return currentManifest != null && fragment != null
                && merger.process(currentManifest, fragment);
    }

    /**
     * Makes a backup of the given file, if it exists, by renaming it to name~
     * (and removing an old name~ file if it exists)
     */
    private static boolean makeBackup(File file) {
        if (!file.exists()) {
            return true;
        }
        if (file.isDirectory()) {
            return false;
        }

        File backupFile = new File(file.getParentFile(), file.getName() + '~');
        if (backupFile.exists()) {
            backupFile.delete();
        }
        return file.renameTo(backupFile);
    }

    private static String getResourceId(Element element) {
        String name = element.getAttribute(ATTR_NAME);
        if (name == null) {
            name = element.getAttribute(ATTR_ID);
        }

        return name;
    }

    /** Instantiates the given template file into the given output file */
    private void instantiate(
            @NonNull final Configuration freemarker,
            @NonNull final Map<String, Object> paramMap,
            @NonNull String relativeFrom,
            @NonNull File to) throws IOException, TemplateException {
        File parentFile = to.getParentFile();
        if (!parentFile.exists()) {
            parentFile.mkdirs();
        }

        // For now, treat extension-less files as directories... this isn't quite right
        // so I should refine this! Maybe with a unique attribute in the template file?
        boolean isDirectory = relativeFrom.indexOf('.') == -1;
        if (isDirectory) {
            // It's a directory
            copyBundledResource(relativeFrom, to);
        } else {
            File from = getFullPath(relativeFrom);
            mLoader.setTemplateFile(from);
            Template template = freemarker.getTemplate(from.getName());
            Writer out = new StringWriter(1024);
            template.process(paramMap, out);
            out.flush();
            String contents = out.toString();

            if (relativeFrom.endsWith(DOT_XML)) {
                XmlFormatStyle formatStyle = XmlFormatStyle.getForFile(new Path(to.getPath()));
                XmlFormatPreferences prefs = XmlFormatPreferences.create();
                contents = XmlPrettyPrinter.prettyPrint(contents, prefs, formatStyle, null);
            }

            writeString(to, contents, true);
        }
    }

    /**
     * Returns the list of files to open when the template has been created
     *
     * @return the list of files to open
     */
    @NonNull
    public List<String> getFilesToOpen() {
        return mOpen;
    }

    /** Copy a bundled resource (part of the plugin .jar file) into the given file system path */
    private final void copyBundledResource(
            @NonNull String relativeFrom,
            @NonNull File output) throws IOException {
        File from = getFullPath(relativeFrom);
        copy(from, output);
    }

    /** Returns true if the given file contains the given bytes */
    private static boolean isIdentical(@Nullable byte[] data, @NonNull File dest)
            throws IOException {
        assert dest.isFile();
        byte[] existing = Files.toByteArray(dest);
        return Arrays.equals(existing, data);
    }

    /**
     * Copies the given source file into the given destination file (where the
     * source is allowed to be a directory, in which case the whole directory is
     * copied recursively)
     */
    private void copy(File src, File dest) throws IOException {
        if (src.isDirectory()){
            if (!dest.exists() && !dest.mkdirs()) {
                throw new IOException("Could not create directory " + dest);
            }
            File[] children = src.listFiles();
            if (children != null) {
                for (File child : children) {
                    copy(child, new File(dest, child.getName()));
                }
            }
        } else {
            if (dest.exists() && isIdentical(Files.toByteArray(src), dest)) {
                return;
            }
            if (!canOverwrite(dest)) {
                return;
            }

            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.copy(src, dest);
        }
    }

    /**
     * A custom {@link TemplateLoader} which locates and provides templates
     * within the plugin .jar file
     */
    private static final class MyTemplateLoader implements TemplateLoader {
        private String mPrefix;

        public void setPrefix(String prefix) {
            mPrefix = prefix;
        }

        public void setTemplateFile(File file) {
            setTemplateParent(file.getParentFile());
        }

        public void setTemplateParent(File parent) {
            mPrefix = parent.getPath();
        }

        @Override
        public Reader getReader(Object templateSource, String encoding) throws IOException {
            URL url = (URL) templateSource;
            return new InputStreamReader(url.openStream(), encoding);
        }

        @Override
        public long getLastModified(Object templateSource) {
            return 0;
        }

        @Override
        public Object findTemplateSource(String name) throws IOException {
            String path = mPrefix != null ? mPrefix + '/' + name : name;
            File file = new File(path);
            if (file.exists()) {
                return file.toURI().toURL();
            }
            return null;
        }

        @Override
        public void closeTemplateSource(Object templateSource) throws IOException {
        }
    }

    /**
     * Returns all the templates with the given prefix
     *
     * @param folder the folder prefix
     * @return the available templates
     */
    @NonNull
    static List<File> getTemplates(@NonNull String folder) {
        List<File> templates = new ArrayList<File>();
        File root = getTemplateRootFolder();
        if (root != null) {
            File[] files = new File(root, folder).listFiles();
            if (files != null) {
                for (File file : files) {
                    templates.add(file);
                }
            }
        }

        // Add in templates from extras/ as well.
        root = getExtraTemplateRootFolder();
        if (root != null) {
            File[] files = new File(root, folder).listFiles();
            if (files != null) {
                for (File file : files) {
                    templates.add(file);
                }
            }
        }

        return templates;
    }

    /**
     * Validates this template to make sure it's supported
     *
     * @return a status object with the error, or null if there is no problem
     */
    @SuppressWarnings("cast") // In Eclipse 3.6.2 cast below is needed
    @Nullable
    public IStatus validateTemplate() {
        TemplateMetadata template = getTemplate();
        if (template != null && !template.isSupported()) {
            String versionString = (String) AdtPlugin.getDefault().getBundle().getHeaders().get(
                    Constants.BUNDLE_VERSION);
            Version version = new Version(versionString);
            return new Status(IStatus.ERROR, AdtPlugin.PLUGIN_ID,
                String.format("This template requires a more recent version of the " +
                        "Android Eclipse plugin. Please update from version %1$d.%2$d.%3$d.",
                        version.getMajor(), version.getMinor(), version.getMicro()));
        }

        return null;
    }
}
