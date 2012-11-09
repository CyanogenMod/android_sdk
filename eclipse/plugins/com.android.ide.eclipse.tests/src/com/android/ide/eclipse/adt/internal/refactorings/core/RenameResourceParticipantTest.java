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
package com.android.ide.eclipse.adt.internal.refactorings.core;

import com.android.annotations.NonNull;
import com.android.ide.eclipse.adt.AdtUtils;
import com.android.ide.eclipse.adt.internal.editors.Hyperlinks;
import com.android.ide.eclipse.adt.internal.editors.layout.refactoring.AdtProjectTest;
import com.android.ide.eclipse.adt.internal.project.BaseProjectHelper;
import com.android.resources.ResourceType;
import com.android.utils.Pair;
import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.internal.corext.refactoring.rename.RenameFieldProcessor;
import org.eclipse.jface.text.Document;
import org.eclipse.jface.text.IDocument;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ltk.core.refactoring.TextFileChange;
import org.eclipse.ltk.core.refactoring.participants.RenameProcessor;
import org.eclipse.ltk.core.refactoring.participants.RenameRefactoring;
import org.eclipse.ltk.core.refactoring.resource.RenameResourceChange;
import org.eclipse.text.edits.TextEdit;

import java.io.File;
import java.io.IOException;

@SuppressWarnings({"javadoc", "restriction"})
public class RenameResourceParticipantTest extends AdtProjectTest {
    public void testRefactor1() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@string/app_name",
                true /*updateReferences*/,
                "myname",

                "CHANGES:\n" +
                "-------\n" +
                "* AndroidManifest.xml - /testRefactor1/AndroidManifest.xml\n" +
                "  <         android:label=\"@string/app_name\"\n" +
                "  <         android:theme=\"@style/AppTheme\" >\n" +
                "  <         <activity\n" +
                "  <             android:name=\"com.example.refactoringtest.MainActivity\"\n" +
                "  <             android:label=\"@string/app_name\" >\n" +
                "  ---\n" +
                "  >         android:label=\"@string/myname\"\n" +
                "  >         android:theme=\"@style/AppTheme\" >\n" +
                "  >         <activity\n" +
                "  >             android:name=\"com.example.refactoringtest.MainActivity\"\n" +
                "  >             android:label=\"@string/myname\" >\n" +
                "\n" +
                "\n" +
                "* strings.xml - /testRefactor1/res/values/strings.xml\n" +
                "  <     <string name=\"app_name\">RefactoringTest</string>\n" +
                "  ---\n" +
                "  >     <string name=\"myname\">RefactoringTest</string>\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor1/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int app_name=0x7f040000;\n" +
                "  ---\n" +
                "  >         public static final int myname=0x7f040000;");
    }

    public void testRefactor2() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@+id/menu_settings",
                true /*updateReferences*/,
                "new_id_for_the_action_bar",

                "CHANGES:\n" +
                "-------\n" +
                "* activity_main.xml - /testRefactor2/res/menu/activity_main.xml\n" +
                "  <         android:id=\"@+id/menu_settings\"\n" +
                "  ---\n" +
                "  >         android:id=\"@+id/new_id_for_the_action_bar\"\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor2/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int menu_settings=0x7f070003;\n" +
                "  ---\n" +
                "  >         public static final int new_id_for_the_action_bar=0x7f070003;");
    }

    public void testRefactor3() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@+id/textView1",
                true /*updateReferences*/,
                "output",

                "CHANGES:\n" +
                "-------\n" +
                "* activity_main.xml - /testRefactor3/res/layout/activity_main.xml\n" +
                "  <         android:id=\"@+id/textView1\"\n" +
                "  <         android:layout_width=\"wrap_content\"\n" +
                "  <         android:layout_height=\"wrap_content\"\n" +
                "  <         android:layout_centerVertical=\"true\"\n" +
                "  <         android:layout_toRightOf=\"@+id/button2\"\n" +
                "  <         android:text=\"@string/hello_world\" />\n" +
                "  <\n" +
                "  <     <Button\n" +
                "  <         android:id=\"@+id/button1\"\n" +
                "  <         android:layout_width=\"wrap_content\"\n" +
                "  <         android:layout_height=\"wrap_content\"\n" +
                "  <         android:layout_alignLeft=\"@+id/textView1\"\n" +
                "  <         android:layout_below=\"@+id/textView1\"\n" +
                "  ---\n" +
                "  >         android:id=\"@+id/output\"\n" +
                "  >         android:layout_width=\"wrap_content\"\n" +
                "  >         android:layout_height=\"wrap_content\"\n" +
                "  >         android:layout_centerVertical=\"true\"\n" +
                "  >         android:layout_toRightOf=\"@+id/button2\"\n" +
                "  >         android:text=\"@string/hello_world\" />\n" +
                "  >\n" +
                "  >     <Button\n" +
                "  >         android:id=\"@+id/button1\"\n" +
                "  >         android:layout_width=\"wrap_content\"\n" +
                "  >         android:layout_height=\"wrap_content\"\n" +
                "  >         android:layout_alignLeft=\"@+id/output\"\n" +
                "  >         android:layout_below=\"@+id/output\"\n" +
                "\n" +
                "\n" +
                "* MainActivity.java - /testRefactor3/src/com/example/refactoringtest/MainActivity.java\n" +
                "  <         View view1 = findViewById(R.id.textView1);\n" +
                "  ---\n" +
                "  >         View view1 = findViewById(R.id.output);\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor3/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int textView1=0x7f070000;\n" +
                "  ---\n" +
                "  >         public static final int output=0x7f070000;");
    }

    public void testRefactor4() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                // same as testRefactor3, but use @id rather than @+id even though @+id is in file
                "@id/textView1",
                true /*updateReferences*/,
                "output",

                "CHANGES:\n" +
                "-------\n" +
                "* activity_main.xml - /testRefactor4/res/layout/activity_main.xml\n" +
                "  <         android:id=\"@+id/textView1\"\n" +
                "  <         android:layout_width=\"wrap_content\"\n" +
                "  <         android:layout_height=\"wrap_content\"\n" +
                "  <         android:layout_centerVertical=\"true\"\n" +
                "  <         android:layout_toRightOf=\"@+id/button2\"\n" +
                "  <         android:text=\"@string/hello_world\" />\n" +
                "  <\n" +
                "  <     <Button\n" +
                "  <         android:id=\"@+id/button1\"\n" +
                "  <         android:layout_width=\"wrap_content\"\n" +
                "  <         android:layout_height=\"wrap_content\"\n" +
                "  <         android:layout_alignLeft=\"@+id/textView1\"\n" +
                "  <         android:layout_below=\"@+id/textView1\"\n" +
                "  ---\n" +
                "  >         android:id=\"@+id/output\"\n" +
                "  >         android:layout_width=\"wrap_content\"\n" +
                "  >         android:layout_height=\"wrap_content\"\n" +
                "  >         android:layout_centerVertical=\"true\"\n" +
                "  >         android:layout_toRightOf=\"@+id/button2\"\n" +
                "  >         android:text=\"@string/hello_world\" />\n" +
                "  >\n" +
                "  >     <Button\n" +
                "  >         android:id=\"@+id/button1\"\n" +
                "  >         android:layout_width=\"wrap_content\"\n" +
                "  >         android:layout_height=\"wrap_content\"\n" +
                "  >         android:layout_alignLeft=\"@+id/output\"\n" +
                "  >         android:layout_below=\"@+id/output\"\n" +
                "\n" +
                "\n" +
                "* MainActivity.java - /testRefactor4/src/com/example/refactoringtest/MainActivity.java\n" +
                "  <         View view1 = findViewById(R.id.textView1);\n" +
                "  ---\n" +
                "  >         View view1 = findViewById(R.id.output);\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor4/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int textView1=0x7f070000;\n" +
                "  ---\n" +
                "  >         public static final int output=0x7f070000;");
    }

    public void testRefactor5() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@layout/activity_main",
                true /*updateReferences*/,
                "newlayout",

                "CHANGES:\n" +
                "-------\n" +
                "* Rename 'testRefactor5/res/layout/activity_main.xml' to 'newlayout.xml'\n" +
                "* Rename 'testRefactor5/res/layout-land/activity_main.xml' to 'newlayout.xml'\n" +
                "* MainActivity.java - /testRefactor5/src/com/example/refactoringtest/MainActivity.java\n" +
                "  <         setContentView(R.layout.activity_main);\n" +
                "  ---\n" +
                "  >         setContentView(R.layout.newlayout);\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor5/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int activity_main=0x7f030000;\n" +
                "  ---\n" +
                "  >         public static final int newlayout=0x7f030000;");
    }

    public void testRefactor6() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@drawable/ic_launcher",
                true /*updateReferences*/,
                "newlauncher",

                "CHANGES:\n" +
                "-------\n" +
                "* AndroidManifest.xml - /testRefactor6/AndroidManifest.xml\n" +
                "  <         android:icon=\"@drawable/ic_launcher\"\n" +
                "  ---\n" +
                "  >         android:icon=\"@drawable/newlauncher\"\n" +
                "\n" +
                "\n" +
                "* Rename 'testRefactor6/res/drawable-hdpi/ic_launcher.png' to 'newlauncher.png'\n" +
                "* Rename 'testRefactor6/res/drawable-ldpi/ic_launcher.png' to 'newlauncher.png'\n" +
                "* Rename 'testRefactor6/res/drawable-mdpi/ic_launcher.png' to 'newlauncher.png'\n" +
                "* Rename 'testRefactor6/res/drawable-xhdpi/ic_launcher.png' to 'newlauncher.png'\n" +
                "* R.java - /testRefactor6/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int ic_launcher=0x7f020000;\n" +
                "  ---\n" +
                "  >         public static final int newlauncher=0x7f020000;");
    }

    public void testRefactor7() throws Exception {
        // Test refactoring initiated on a file rename
        IProject project = createProject(TEST_PROJECT);
        IFile file = project.getFile("res/layout/activity_main.xml");
        checkRefactoring(
                project,
                file,
                true /*updateReferences*/,
                "newlayout",

                "CHANGES:\n" +
                "-------\n" +
                "* Rename 'testRefactor7/res/layout/activity_main.xml' to 'newlayout.xml'\n" +
                "* Rename 'testRefactor7/res/layout-land/activity_main.xml' to 'newlayout.xml'\n" +
                "* MainActivity.java - /testRefactor7/src/com/example/refactoringtest/MainActivity.java\n" +
                "  <         setContentView(R.layout.activity_main);\n" +
                "  ---\n" +
                "  >         setContentView(R.layout.newlayout);\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor7/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int activity_main=0x7f030000;\n" +
                "  ---\n" +
                "  >         public static final int newlayout=0x7f030000;");
    }

    public void testRefactor8() throws Exception {
        // Test refactoring initiated on a Java field rename
        IProject project = createProject(TEST_PROJECT);
        IJavaProject javaProject = BaseProjectHelper.getJavaProject(project);
        assertNotNull(javaProject);
        IType type = javaProject.findType("com.example.refactoringtest.R.layout");
        if (type == null || !type.exists()) {
            type = javaProject.findType("com.example.refactoringtest.R$layout");
            System.out.println("Had to switch to $ notation");
        }
        assertNotNull(type);
        assertTrue(type.exists());
        IField field = type.getField("activity_main");
        assertNotNull(field);
        assertTrue(field.exists());

        checkRefactoring(
                project,
                field,
                true /*updateReferences*/,
                "newlauncher",

                "CHANGES:\n" +
                "-------\n" +
                "* MainActivity.java - /testRefactor8/src/com/example/refactoringtest/MainActivity.java\n" +
                "  <         setContentView(R.layout.activity_main);\n" +
                "  ---\n" +
                "  >         setContentView(R.layout.newlauncher);\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor8/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int activity_main=0x7f030000;\n" +
                "  ---\n" +
                "  >         public static final int newlauncher=0x7f030000;\n" +
                "\n" +
                "\n" +
                "* Rename 'testRefactor8/res/layout/activity_main.xml' to 'newlauncher.xml'\n" +
                "* Rename 'testRefactor8/res/layout-land/activity_main.xml' to 'newlauncher.xml'");
    }

    public void testInvalidName() throws Exception {
        checkRefactoring(
                TEST_PROJECT,
                "@drawable/ic_launcher",
                true /*updateReferences*/,
                "Newlauncher",

                "<ERROR\n" +
                "\t\n" +
                "ERROR: File-based resource names must start with a lowercase letter.\n" +
                "Context: <Unspecified context>\n" +
                "code: none\n" +
                "Data: null\n" +
                ">");
    }

    public void testRefactor9() throws Exception {
        // same as testRefactor4, but not updating references
        checkRefactoring(
                TEST_PROJECT,
                "@id/textView1",
                false /*updateReferences*/,
                "output",

                "CHANGES:\n" +
                "-------\n" +
                "* activity_main.xml - /testRefactor9/res/layout/activity_main.xml\n" +
                "  <         android:id=\"@+id/textView1\"\n" +
                "  ---\n" +
                "  >         android:id=\"@+id/output\"\n" +
                "\n" +
                "\n" +
                "* R.java - /testRefactor9/gen/com/example/refactoringtest/R.java\n" +
                "  <         public static final int textView1=0x7f070000;\n" +
                "  ---\n" +
                "  >         public static final int output=0x7f070000;");
    }

    // ---- Only test infrastructure below ----

    private void checkRefactoring(
            @NonNull Object[] testData,
            @NonNull Object resource,
            boolean updateReferences,
            @NonNull String newName,
            @NonNull String expected) throws Exception {
        IProject project = createProject(testData);
        checkRefactoring(project, resource, updateReferences, newName, expected);
    }

    private void checkRefactoring(
            @NonNull IProject project,
            @NonNull Object resource,
            boolean updateReferences,
            @NonNull String newName,
            @NonNull String expected) throws Exception {
        RenameProcessor processor = null;
        if (resource instanceof String) {
            String url = (String) resource;
            assert url.startsWith("@") : resource;
            Pair<ResourceType, String> pair = Hyperlinks.parseResource(url);
            assertNotNull(url, pair);
            ResourceType type = pair.getFirst();
            String currentName = pair.getSecond();
            RenameResourceProcessor p;
            p = new RenameResourceProcessor(project, type, currentName, newName);
            p.setUpdateReferences(updateReferences);
            processor = p;
        } else if (resource instanceof IResource) {
            IResource r = (IResource) resource;
            org.eclipse.ltk.internal.core.refactoring.resource.RenameResourceProcessor p;
            p = new org.eclipse.ltk.internal.core.refactoring.resource.RenameResourceProcessor(r);
            String fileName = r.getName();
            int dot = fileName.indexOf('.');
            String extension = (dot != -1) ? fileName.substring(dot) : "";
            p.setNewResourceName(newName + extension);
            p.setUpdateReferences(updateReferences);
            processor = p;
        } else if (resource instanceof IField) {
            RenameFieldProcessor p = new RenameFieldProcessor((IField) resource);
            p.setNewElementName(newName);
            p.setUpdateReferences(updateReferences);
            processor = p;
        } else {
            fail("Unsupported resource element in tests: " + resource);
        }

        assertNotNull(processor);

        RenameRefactoring refactoring = new RenameRefactoring(processor);
        RefactoringStatus status = refactoring.checkAllConditions(new NullProgressMonitor());
        assertNotNull(status);
        if (!status.isOK()) {
            assertEquals(status.toString(), expected);
            return;
        }
        assertTrue(status.toString(), status.isOK());
        Change change = refactoring.createChange(new NullProgressMonitor());
        assertNotNull(change);
        String explanation = "CHANGES:\n-------\n" + describe(change);
        if (!expected.trim().equals(explanation.trim())) { // allow trimming endlines in expected
            assertEquals(expected, explanation);
        }
   }

    private IProject createProject(Object[] testData) throws Exception {
        String name = getName();
        IProject project = createProject(name);
        File projectDir = AdtUtils.getAbsolutePath(project).toFile();
        assertNotNull(projectDir);
        assertTrue(projectDir.getPath(), projectDir.exists());
        createTestDataDir(projectDir, testData);
        project.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());

        for (int i = 0; i < testData.length; i+= 2) {
            assertTrue(testData[i].toString(), testData[i] instanceof String);
            String relative = (String) testData[i];
            IResource member = project.findMember(relative);
            assertNotNull(relative, member);
            assertTrue(member.getClass().getSimpleName(), member instanceof IFile);
        }

        return project;
    }

    private String describe(Change change) throws Exception {
        StringBuilder sb = new StringBuilder(1000);
        describe(sb, change, 0);

        // Trim trailing space
        for (int i = sb.length() - 1; i >= 0; i--) {
            if (!Character.isWhitespace(sb.charAt(i))) {
                sb.setLength(i + 1);
                break;
            }
        }

        return sb.toString();
    }

    private void describe(StringBuilder sb, Change change, int indent) throws Exception {
        if (change instanceof CompositeChange
                && ((CompositeChange) change).isSynthetic()) {
            // Don't display information about synthetic changes
        } else {
            // Describe this change
            indent(sb, indent);
            sb.append("* ");
            sb.append(change.getName());
            if (change instanceof TextFileChange) {
                TextFileChange tfc = (TextFileChange) change;
                sb.append(" - ");
                sb.append(tfc.getFile().getFullPath());
                sb.append('\n');
            }
            if (change instanceof TextFileChange) {
                TextFileChange tfc = (TextFileChange) change;
                TextEdit edit = tfc.getEdit();
                IFile file = tfc.getFile();
                byte[] bytes = ByteStreams.toByteArray(file.getContents());
                String before = new String(bytes, Charsets.UTF_8);
                IDocument document = new Document();
                document.replace(0, 0, before);
                edit.apply(document);
                String after = document.get();
                String diff = getDiff(before, after);
                for (String line : Splitter.on('\n').split(diff)) {
                    if (!line.trim().isEmpty()) {
                        indent(sb, indent + 1);
                        sb.append(line);
                    }
                    sb.append('\n');
                }
            } else if (change instanceof RenameResourceChange) {
                // Change name, appended above, is adequate
            } else {
                indent(sb, indent);
                sb.append("<unknown change type " + change.getClass().getName() + ">");
            }
            sb.append('\n');
        }

        if (change instanceof CompositeChange) {
            CompositeChange composite = (CompositeChange) change;
            Change[] children = composite.getChildren();
            for (Change child : children) {
                describe(sb, child, indent + (composite.isSynthetic() ? 0 : 1));
            }
        }
    }

    private static void indent(StringBuilder sb, int indent) {
        for (int i = 0; i < indent; i++) {
            sb.append("  ");
        }
    }

    private void createTestDataDir(File dir, Object[] data) throws IOException {
        for (int i = 0, n = data.length; i < n; i += 2) {
            assertTrue("Must be a path: " + data[i], data[i] instanceof String);
            String relativePath = ((String) data[i]).replace('/', File.separatorChar);
            File to = new File(dir, relativePath);
            File parent = to.getParentFile();
            if (!parent.exists()) {
                boolean mkdirs = parent.mkdirs();
                assertTrue(to.getPath(), mkdirs);
            }

            Object o = data[i + 1];
            if (o instanceof String) {
                String contents = (String) o;
                Files.write(contents, to, Charsets.UTF_8);
            } else if (o instanceof byte[]) {
                Files.write((byte[]) o, to);
            } else {
                fail("Data must be a String or a byte[] for " + to);
            }
        }
    }

    // Test sources

    private static final String SAMPLE_MANIFEST =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    package=\"com.example.refactoringtest\"\n" +
            "    android:versionCode=\"1\"\n" +
            "    android:versionName=\"1.0\" >\n" +
            "\n" +
            "    <uses-sdk\n" +
            "        android:minSdkVersion=\"8\"\n" +
            "        android:targetSdkVersion=\"17\" />\n" +
            "\n" +
            "    <application\n" +
            "        android:icon=\"@drawable/ic_launcher\"\n" +
            "        android:label=\"@string/app_name\"\n" +
            "        android:theme=\"@style/AppTheme\" >\n" +
            "        <activity\n" +
            "            android:name=\"com.example.refactoringtest.MainActivity\"\n" +
            "            android:label=\"@string/app_name\" >\n" +
            "            <intent-filter>\n" +
            "                <action android:name=\"android.intent.action.MAIN\" />\n" +
            "\n" +
            "                <category android:name=\"android.intent.category.LAUNCHER\" />\n" +
            "            </intent-filter>\n" +
            "        </activity>\n" +
            "    </application>\n" +
            "\n" +
            "</manifest>";

    private static final String SAMPLE_MAIN_ACTIVITY =
            "package com.example.refactoringtest;\n" +
            "\n" +
            "import android.os.Bundle;\n" +
            "import android.app.Activity;\n" +
            "import android.view.Menu;\n" +
            "import android.view.View;\n" +
            "\n" +
            "public class MainActivity extends Activity {\n" +
            "\n" +
            "    @Override\n" +
            "    protected void onCreate(Bundle savedInstanceState) {\n" +
            "        super.onCreate(savedInstanceState);\n" +
            "        setContentView(R.layout.activity_main);\n" +
            "        View view1 = findViewById(R.id.textView1);\n" +
            "    }\n" +
            "\n" +
            "    @Override\n" +
            "    public boolean onCreateOptionsMenu(Menu menu) {\n" +
            "        // Inflate the menu; this adds items to the action bar if it is present.\n" +
            "        getMenuInflater().inflate(R.menu.activity_main, menu);\n" +
            "        return true;\n" +
            "    }\n" +
            "\n" +
            "}\n";

    private static final String SAMPLE_LAYOUT =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    tools:context=\".MainActivity\" >\n" +
            "\n" +
            "    <TextView\n" +
            "        android:id=\"@+id/textView1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_centerVertical=\"true\"\n" +
            "        android:layout_toRightOf=\"@+id/button2\"\n" +
            "        android:text=\"@string/hello_world\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button1\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_alignLeft=\"@+id/textView1\"\n" +
            "        android:layout_below=\"@+id/textView1\"\n" +
            "        android:layout_marginLeft=\"22dp\"\n" +
            "        android:layout_marginTop=\"24dp\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "    <Button\n" +
            "        android:id=\"@+id/button2\"\n" +
            "        android:layout_width=\"wrap_content\"\n" +
            "        android:layout_height=\"wrap_content\"\n" +
            "        android:layout_alignParentLeft=\"true\"\n" +
            "        android:layout_alignParentTop=\"true\"\n" +
            "        android:text=\"Button\" />\n" +
            "\n" +
            "</RelativeLayout>";

    private static final String SAMPLE_LAYOUT_2 =
            "<RelativeLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"\n" +
            "    xmlns:tools=\"http://schemas.android.com/tools\"\n" +
            "    android:layout_width=\"match_parent\"\n" +
            "    android:layout_height=\"match_parent\"\n" +
            "    tools:context=\".MainActivity\" >\n" +
            "\n" +
            "\n" +
            "</RelativeLayout>";


    private static final String SAMPLE_MENU =
            "<menu xmlns:android=\"http://schemas.android.com/apk/res/android\" >\n" +
            "\n" +
            "    <item\n" +
            "        android:id=\"@+id/menu_settings\"\n" +
            "        android:orderInCategory=\"100\"\n" +
            "        android:showAsAction=\"never\"\n" +
            "        android:title=\"@string/menu_settings\"/>\n" +
            "\n" +
            "</menu>";

    private static final String SAMPLE_STRINGS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<resources>\n" +
            "\n" +
            "    <string name=\"app_name\">RefactoringTest</string>\n" +
            "    <string name=\"hello_world\">Hello world!</string>\n" +
            "    <string name=\"menu_settings\">Settings</string>\n" +
            "\n" +
            "</resources>";

    private static final String SAMPLE_STYLES =
            "<resources>\n" +
            "\n" +
            "    <!--\n" +
            "        Base application theme, dependent on API level. This theme is replaced\n" +
            "        by AppBaseTheme from res/values-vXX/styles.xml on newer devices.\n" +
            "    -->\n" +
            "    <style name=\"AppBaseTheme\" parent=\"android:Theme.Light\">\n" +
            "        <!--\n" +
            "            Theme customizations available in newer API levels can go in\n" +
            "            res/values-vXX/styles.xml, while customizations related to\n" +
            "            backward-compatibility can go here.\n" +
            "        -->\n" +
            "    </style>\n" +
            "\n" +
            "    <!-- Application theme. -->\n" +
            "    <style name=\"AppTheme\" parent=\"AppBaseTheme\">\n" +
            "        <!-- All customizations that are NOT specific to a particular API-level can go here. -->\n" +
            "    </style>\n" +
            "\n" +
            "</resources>";

    private static final String SAMPLE_R =
            "/* AUTO-GENERATED FILE.  DO NOT MODIFY.\n" +
            " *\n" +
            " * This class was automatically generated by the\n" +
            " * aapt tool from the resource data it found.  It\n" +
            " * should not be modified by hand.\n" +
            " */\n" +
            "\n" +
            "package com.example.refactoringtest;\n" +
            "\n" +
            "public final class R {\n" +
            "    public static final class attr {\n" +
            "    }\n" +
            "    public static final class drawable {\n" +
            "        public static final int ic_launcher=0x7f020000;\n" +
            "    }\n" +
            "    public static final class id {\n" +
            "        public static final int button1=0x7f070002;\n" +
            "        public static final int button2=0x7f070001;\n" +
            "        public static final int menu_settings=0x7f070003;\n" +
            "        public static final int textView1=0x7f070000;\n" +
            "    }\n" +
            "    public static final class layout {\n" +
            "        public static final int activity_main=0x7f030000;\n" +
            "    }\n" +
            "    public static final class menu {\n" +
            "        public static final int activity_main=0x7f060000;\n" +
            "    }\n" +
            "    public static final class string {\n" +
            "        public static final int app_name=0x7f040000;\n" +
            "        public static final int hello_world=0x7f040001;\n" +
            "        public static final int menu_settings=0x7f040002;\n" +
            "    }\n" +
            "    public static final class style {\n" +
            "        /** \n" +
            "        Base application theme, dependent on API level. This theme is replaced\n" +
            "        by AppBaseTheme from res/values-vXX/styles.xml on newer devices.\n" +
            "    \n" +
            "\n" +
            "            Theme customizations available in newer API levels can go in\n" +
            "            res/values-vXX/styles.xml, while customizations related to\n" +
            "            backward-compatibility can go here.\n" +
            "        \n" +
            "\n" +
            "        Base application theme for API 11+. This theme completely replaces\n" +
            "        AppBaseTheme from res/values/styles.xml on API 11+ devices.\n" +
            "    \n" +
            " API 11 theme customizations can go here. \n" +
            "\n" +
            "        Base application theme for API 14+. This theme completely replaces\n" +
            "        AppBaseTheme from BOTH res/values/styles.xml and\n" +
            "        res/values-v11/styles.xml on API 14+ devices.\n" +
            "    \n" +
            " API 14 theme customizations can go here. \n" +
            "         */\n" +
            "        public static final int AppBaseTheme=0x7f050000;\n" +
            "        /**  Application theme. \n" +
            " All customizations that are NOT specific to a particular API-level can go here. \n" +
            "         */\n" +
            "        public static final int AppTheme=0x7f050001;\n" +
            "    }\n" +
            "}\n";

    private static final Object[] TEST_PROJECT = new Object[] {
        "AndroidManifest.xml",
        SAMPLE_MANIFEST,

        "src/com/example/refactoringtest/MainActivity.java",
        SAMPLE_MAIN_ACTIVITY,

        "gen/com/example/refactoringtest/R.java",
        SAMPLE_R,

        "res/drawable-xhdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-hdpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-ldpi/ic_launcher.png",
        new byte[] { 0 },
        "res/drawable-mdpi/ic_launcher.png",
        new byte[] { 0 },

        "res/layout/activity_main.xml",
        SAMPLE_LAYOUT,

        "res/layout-land/activity_main.xml",
        SAMPLE_LAYOUT_2,

        "res/menu/activity_main.xml",
        SAMPLE_MENU,

        "res/values/strings.xml",   // file 3
        SAMPLE_STRINGS,

        "res/values/styles.xml",   // file 3
        SAMPLE_STYLES,
    };
}