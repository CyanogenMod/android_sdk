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

    public void testParseProcRankEclair() throws IOException {
        String memInfo =
                "   51   39408K   37908K   18731K   14936K  system_server\n" +
                "   96   27432K   27432K    9501K    6816K  android.process.acore\n" +
                "   27     248K     248K      83K      76K  /system/bin/debuggerd\n";
        BufferedReader br = new BufferedReader(new StringReader(memInfo));
        List<DataValue> data = BugReportParser.readProcRankDataset(br,
                "  PID      Vss      Rss      Pss      Uss  cmdline\n");

        assertEquals(3, data.size());
        assertEquals("debuggerd", data.get(2).name);
        if (data.get(0).value - 18731 > 0.0002) {
            fail("Unexpected PSS Value " + data.get(0).value);
        }
    }

    public void testParseProcRankJb() throws IOException {
        String memInfo =
                "  675  101120K  100928K   63452K   52624K  system_server\n" +
                "10170   82100K   82012K   58246K   53580K  com.android.chrome:sandboxed_process0\n" +
                " 8742   27296K   27224K    6849K    5620K  com.google.android.apps.walletnfcrel\n" +
                "                          ------   ------  ------\n" +
                "                         480598K  394172K  TOTAL\n" +
                "\n" +
                "RAM: 1916984K total, 886404K free, 72036K buffers, 482544K cached, 456K shmem, 34864K slab\n";
        BufferedReader br = new BufferedReader(new StringReader(memInfo));
        List<DataValue> data = BugReportParser.readProcRankDataset(br,
                "  PID      Vss      Rss      Pss      Uss  cmdline\n");

        assertEquals(3, data.size());
    }

    public void testParseMeminfoEclair() throws IOException {
        String memInfo =
                "------ MEMORY INFO ------\n" +
                "MemTotal:         516528 kB\n" +
                "MemFree:          401036 kB\n" +
                "Buffers:               0 kB\n" +
                "  PID      Vss      Rss      Pss      Uss  cmdline\n" +
                "   51   39408K   37908K   18731K   14936K  system_server\n" +
                "   96   27432K   27432K    9501K    6816K  android.process.acore\n" +
                "  297   23348K   23348K    5245K    2276K  com.android.gallery\n";
        BufferedReader br = new BufferedReader(new StringReader(memInfo));
        List<DataValue> data = BugReportParser.readMeminfoDataset(br);
        assertEquals(5, data.size());

        assertEquals("Free", data.get(0).name);
    }

    public void testParseMeminfoJb() throws IOException {

        String memInfo = // note: This dataset does not have all entries, so the totals will be off
                "------ MEMORY INFO ------\n" +
                "MemTotal:        1916984 kB\n" +
                "MemFree:          888048 kB\n" +
                "Buffers:           72036 kB\n" +
                "  PID      Vss      Rss      Pss      Uss  cmdline\n" +
                "  675  101120K  100928K   63452K   52624K  system_server\n" +
                "10170   82100K   82012K   58246K   53580K  com.android.chrome:sandboxed_process0\n" +
                " 8742   27296K   27224K    6849K    5620K  com.google.android.apps.walletnfcrel\n" +
                "                          ------   ------  ------\n" +
                "                         480598K  394172K  TOTAL\n" +
                "\n" +
                "RAM: 1916984K total, 886404K free, 72036K buffers, 482544K cached, 456K shmem, 34864K slab\n";

        BufferedReader br = new BufferedReader(new StringReader(memInfo));
        List<DataValue> data = BugReportParser.readMeminfoDataset(br);

        assertEquals(6, data.size());
    }
}
