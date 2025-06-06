/*                 _                 
 *       /\       (_)            
 *      /  \__   ___ _ __ ___  _ __  
 *     / /\ \ \ / / | '__/ _ \| '_ \ 
 *    / ____ \ V /| | | | (_) | | | |
 *   /_/    \_\_/ |_|_|  \___/|_| |_|
 *
 *
 * Copyright 2025 Aviron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.jlangch.aviron.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.github.jlangch.aviron.ex.AvironException;


public class Shell {

    public static ShellResult execCmd(final String... command) throws IOException {
        final String cmdFormatted = formatCmd(command);

        try {
            final Process proc = Runtime.getRuntime().exec(command);

            return getShellResult(proc);
        }
        catch(Exception ex) {
            throw new AvironException("Failed to run command: " + cmdFormatted, ex);
        }
    }

    public static ShellBackgroundResult execCmdBackground(final String... command) throws IOException {
        validateLinuxOrMacOSX("Shell::execCmdBackground");

        final String cmdFormatted = formatCmd(command);

        try {
            final File nohup = File.createTempFile("nohup-", ".out");
            nohup.deleteOnExit();
            
            final String cmd = cmdFormatted + " 2>&1 >" + nohup.getAbsolutePath() + " &";

            final Process proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});


            return new ShellBackgroundResult(getShellResult(proc), nohup);
        }
        catch(Exception ex) {
            throw new AvironException(
                    "Failed to run nohup command: /bin/sh -c " 
                    + cmdFormatted 
                    + " 2>&1 >nohup.out &", 
                    ex);
        }
    }

    public static ShellBackgroundResult execCmdBackgroundNohup(final String... command) throws IOException {
        validateLinuxOrMacOSX("Shell::execCmdNohup");

        final String cmdFormatted = formatCmd(command);

        try {
            final File nohup = File.createTempFile("nohup-", ".out");
            nohup.deleteOnExit();
            
            final String cmd = "nohup " + cmdFormatted + " 2>&1 >" + nohup.getAbsolutePath() + " &";

            final Process proc = Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", cmd});

            return new ShellBackgroundResult(getShellResult(proc), nohup);
        }
        catch(Exception ex) {
            throw new AvironException(
                    "Failed to run nohup command: /bin/sh -c nohup " 
                    + cmdFormatted 
                    + " 2>&1 >nohup.out &", 
                    ex);
        }
    }

    public static List<String> pgrep(final String process) {
        validateLinuxOrMacOSX("Shell::pgrep");

        try {
            final ShellResult r = Shell.execCmd("pgrep", process);
            return r.isZeroExitCode()
                    ? r.getStdoutLines()
                       .stream()
                       .filter(s -> !StringUtils.isBlank(s))
                       .collect(Collectors.toList())
                    : new ArrayList<>();
        }
        catch(IOException ex) {
            throw new AvironException("Failed to get " + process + " PIDs", ex);
        }
    }

    public static void kill(final Signal signal, final String pid) {
        validateLinuxOrMacOSX("Shell::kill");

        if (!StringUtils.isBlank(pid)) {
            try {
                final ShellResult r = Shell.execCmd("kill", "-" + signal.signal(), pid);
                if (!r.isZeroExitCode()) {
                    throw new AvironException(
                            "Failed to kill process (" + pid + ").\n"
                            + "\nExit code: " + r.getExitCode()
                            + "\nError msg: " + r.getStderr());
                }
            }
            catch(IOException ex) {
                throw new AvironException(
                        "Failed to kill the process " + pid, ex);
            }
        }
    }

    public static void validateLinuxOrMacOSX(final String fnName) {
         if (!(OS.isLinux() || OS.isMacOSX())) {
             throw new AvironException(fnName + " is available for Linux and MacOS only!");
         }
    }

    private static String formatCmd(final String... command) {
        return String.join(" ", Arrays.asList(command));
    }

    private static String slurp(final InputStream is) throws IOException {
        try (BufferedReader br = new BufferedReader(
                                        new InputStreamReader(
                                                is, StandardCharsets.UTF_8))) {
            return br.lines().collect(Collectors.joining("\n"));
        }
    }
    
    private static ShellResult getShellResult(final Process proc) 
    throws IOException, InterruptedException {
        final int exitCode = proc.waitFor();

        final String stdout = slurp(proc.getInputStream());
        final String stderr = slurp(proc.getErrorStream());

        return new ShellResult(stdout, stderr, exitCode);
    }
}
