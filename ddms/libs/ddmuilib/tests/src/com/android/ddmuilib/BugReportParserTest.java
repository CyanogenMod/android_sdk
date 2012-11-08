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

package com.android.ddmuilib;

import com.android.ddmuilib.SysinfoPanel.BugReportParser;
import com.android.ddmuilib.SysinfoPanel.BugReportParser.DataValue;

import junit.framework.TestCase;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class BugReportParserTest extends TestCase {
    public void testParseEclairCpuDataSet() throws IOException {
        String cpuInfo =
                "Currently running services:\n" +
                "   cpuinfo\n" +
                " ----------------------------------------------------------------------------\n" +
                " DUMP OF SERVICE cpuinfo:\n" +
                " Load: 0.53 / 0.11 / 0.04\n" +
                " CPU usage from 33406ms to 28224ms ago:\n" +
                "   system_server: 56% = 42% user + 13% kernel / faults: 6724 minor 9 major\n" +
                "   bootanimation: 1% = 0% user + 0% kernel\n" +
                "   zygote: 0% = 0% user + 0% kernel / faults: 146 minor\n" +
                " TOTAL: 98% = 67% user + 30% kernel;\n";
        BufferedReader br = new BufferedReader(new StringReader(cpuInfo));
        List<DataValue> data = BugReportParser.readCpuDataset(br);

        assertEquals(4, data.size());
        assertEquals("system_server (user)", data.get(0).name);
        assertEquals("Idle", data.get(3).name);
    }

    public void testParseJbCpuDataSet() throws IOException {
        String cpuInfo =
                "Load: 1.0 / 1.02 / 0.97\n" +
                "CPU usage from 96307ms to 36303ms ago:\n" +
                "  0.4% 675/system_server: 0.3% user + 0.1% kernel / faults: 198 minor\n" +
                "  0.1% 173/mpdecision: 0% user + 0.1% kernel\n" +
                "  0% 2856/kworker/0:2: 0% user + 0% kernel\n" +
                "  0% 3128/kworker/0:0: 0% user + 0% kernel\n" +
                "0.3% TOTAL: 0.1% user + 0% kernel + 0% iowait\n";
        BufferedReader br = new BufferedReader(new StringReader(cpuInfo));
        List<DataValue> data = BugReportParser.readCpuDataset(br);

        assertEquals(4, data.size());
        assertEquals("675/system_server (user)", data.get(0).name);
        assertEquals("Idle", data.get(3).name);
    }
}
