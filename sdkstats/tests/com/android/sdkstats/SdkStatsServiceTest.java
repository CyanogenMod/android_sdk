/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.sdkstats;

import java.util.HashMap;
import java.util.Map;

import junit.framework.TestCase;

public class SdkStatsServiceTest extends TestCase {

    private static class MockSdkStatsService extends SdkStatsService {

        private final String mOsName;
        private final String mOsVersion;
        private final String mOsArch;
        private final String mJavaVersion;
        private final Map<String, String> mEnvVars = new HashMap<String, String>();

        public MockSdkStatsService(String osName,
                String osVersion,
                String osArch,
                String javaVersion) {
                    mOsName = osName;
                    mOsVersion = osVersion;
                    mOsArch = osArch;
                    mJavaVersion = javaVersion;
        }

        public void setSystemEnv(String varName, String value) {
            mEnvVars.put(varName, value);
        }

        @Override
        protected String getSystemProperty(String name) {
            if (SdkStatsService.SYS_PROP_OS_NAME.equals(name)) {
                return mOsName;
            } else if (SdkStatsService.SYS_PROP_OS_VERSION.equals(name)) {
                return mOsVersion;
            } else if (SdkStatsService.SYS_PROP_OS_ARCH.equals(name)) {
                return mOsArch;
            } else if (SdkStatsService.SYS_PROP_JAVA_VERSION.equals(name)) {
                return mJavaVersion;
            }
            // Don't use current properties values, we don't want the tests to be flaky
            fail("SdkStatsServiceTest doesn't define a system.property for " + name);
            return null;
        }

        @Override
        protected String getSystemEnv(String name) {
            if (mEnvVars.containsKey(name)) {
                return mEnvVars.get(name);
            }
            // Don't use current env vars, we don't want the tests to be flaky
            fail("SdkStatsServiceTest doesn't define a system.getenv for " + name);
            return null;
        }

    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testSdkStatsService_getJvmArch() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i386", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i486", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Linux",   "4.0", "i486-linux", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i586", "1.7");
        assertEquals("x86", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "i686", "1.7");
        assertEquals("x86", m.getJvmArch());

        m = new MockSdkStatsService("Mac OS",  "10.0", "x86_64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Mac OS",  "8.0", "PowerPC", "1.7");
        assertEquals("ppc", m.getJvmArch());

        m = new MockSdkStatsService("Mac OS",  "4.0", "x86_64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "ia64", "1.7");
        assertEquals("x86_64", m.getJvmArch());
        m = new MockSdkStatsService("Windows", "4.0", "amd64", "1.7");
        assertEquals("x86_64", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", "atom", "1.7");
        assertEquals("atom", m.getJvmArch());

        // 32 chars max
        m = new MockSdkStatsService("Windows", "4.0",
                "one3456789ten3456789twenty6789thirty6789", "1.7");
        assertEquals("one3456789ten3456789twenty6789th", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", "", "1.7");
        assertEquals("unknown", m.getJvmArch());

        m = new MockSdkStatsService("Windows", "4.0", null, "1.7");
        assertEquals("unknown", m.getJvmArch());
    }

    public void testSdkStatsService_getJvmVersion() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("1.7", m.getJvmVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", "");
        assertEquals("unknown", m.getJvmVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", null);
        assertEquals("unknown", m.getJvmVersion());

        // 8 chars max
        m = new MockSdkStatsService("Windows", "4.0", "x86",
                "one3456789ten3456789twenty6789thirty6789");
        assertEquals("one34567", m.getJvmVersion());
    }

    public void testSdkStatsService_getJvmInfo() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("1.7-x86", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", "amd64", "1.7.8_09");
        assertEquals("1.7-x86_64", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", "", "");
        assertEquals("unknown-unknown", m.getJvmInfo());

        m = new MockSdkStatsService("Windows", "4.0", null, null);
        assertEquals("unknown-unknown", m.getJvmInfo());

        // 8+32 chars max
        m = new MockSdkStatsService("Windows", "4.0",
                "one3456789ten3456789twenty6789thirty6789",
                "one3456789ten3456789twenty6789thirty6789");
        assertEquals("one34567-one3456789ten3456789twenty6789th", m.getJvmInfo());
    }

    public void testSdkStatsService_getOsVersion() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Windows", "4.0.32", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4.0", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4", "x86", "1.7.8_09");
        assertEquals(null, m.getOsVersion());

        m = new MockSdkStatsService("Windows", "4.0;extrainfo", "x86", "1.7.8_09");
        assertEquals("4.0", m.getOsVersion());

        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("10.8", m.getOsVersion());

        m = new MockSdkStatsService("Mac OS", "10.8", "x86_64", "1.7.8_09");
        assertEquals("10.8", m.getOsVersion());

        m = new MockSdkStatsService("Other", "", "x86_64", "1.7.8_09");
        assertEquals(null, m.getOsVersion());

        m = new MockSdkStatsService("Other", null, "x86_64", "1.7.8_09");
        assertEquals(null, m.getOsVersion());
    }

    public void testSdkStatsService_getOsArch() {
        MockSdkStatsService m;

        // 64 bit jvm
        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86_64", "1.7.8_09");
        assertEquals("x86_64", m.getOsArch());

        // 32 bit jvm with 32 vs 64 bit os
        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", null);
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", "AMD64");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Windows", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("PROCESSOR_ARCHITEW6432", "IA64");
        assertEquals("x86_64", m.getOsArch());

        // 32 bit jvm with 32 vs 64 bit os
        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", null);
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "i686-linux");
        assertEquals("x86", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "AMD64");
        assertEquals("x86_64", m.getOsArch());

        m = new MockSdkStatsService("Linux", "8.32", "x86", "1.7.8_09");
        m.setSystemEnv("HOSTTYPE", "x86_64");
        assertEquals("x86_64", m.getOsArch());
    }

    public void testSdkStatsService_getOsName() {
        MockSdkStatsService m;

        m = new MockSdkStatsService("Mac OS", "10.8.32", "x86_64", "1.7.8_09");
        assertEquals("mac-10.8", m.getOsName());

        m = new MockSdkStatsService("mac", "10", "x86", "1.7.8_09");
        assertEquals("mac", m.getOsName());

        m = new MockSdkStatsService("Windows", "6.2", "x86_64", "1.7.8_09");
        assertEquals("win-6.2", m.getOsName());

        m = new MockSdkStatsService("win", "6.2", "x86", "1.7.8_09");
        assertEquals("win-6.2", m.getOsName());

        m = new MockSdkStatsService("win", "6", "x86_64", "1.7.8_09");
        assertEquals("win", m.getOsName());

        m = new MockSdkStatsService("Linux", "foobuntu-32", "x86", "1.7.8_09");
        assertEquals("linux", m.getOsName());

        m = new MockSdkStatsService("linux", "1", "x86_64", "1.7.8_09");
        assertEquals("linux", m.getOsName());

        m = new MockSdkStatsService("PowerPC", "32", "ppc", "1.7.8_09");
        assertEquals("PowerPC", m.getOsName());

        m = new MockSdkStatsService("freebsd", "42", "x86_64", "1.7.8_09");
        assertEquals("freebsd", m.getOsName());

        m = new MockSdkStatsService("openbsd", "43", "x86_64", "1.7.8_09");
        assertEquals("openbsd", m.getOsName());

        // 32 chars max
        m = new MockSdkStatsService("one3456789ten3456789twenty6789thirty6789",
                "42", "x86_64", "1.7.8_09");
        assertEquals("one3456789ten3456789twenty6789th", m.getOsName());
    }
}
