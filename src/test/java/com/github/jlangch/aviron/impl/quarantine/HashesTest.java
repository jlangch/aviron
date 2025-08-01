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
package com.github.jlangch.aviron.impl.quarantine;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.File;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import com.github.jlangch.aviron.util.DemoFilestore;


class HashesTest {

    @Test
    void testDataHash() {
        final String hashFile1 = Hashes.hashData("MD5", "SALT", "TEST1".getBytes(Charset.defaultCharset()));
        final String hashFile2 = Hashes.hashData("MD5", "SALT", "TEST2".getBytes(Charset.defaultCharset()));

        assertNotNull(hashFile1);
        assertNotNull(hashFile2);

        assertNotEquals(hashFile1, hashFile2);
     }

    @Test
    void testFileHash() {
        try(DemoFilestore demoFS = new DemoFilestore()) {
            demoFS.createFilestoreSubDir("000");

            final File f1 = demoFS.createFilestoreFile("000", "1.txt");


            final String hashFile1 = Hashes.hashFile("MD5", "SALT", f1);

            assertNotNull(hashFile1);
        }
     }

}
