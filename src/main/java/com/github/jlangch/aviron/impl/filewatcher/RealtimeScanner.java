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
package com.github.jlangch.aviron.impl.filewatcher;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.github.jlangch.aviron.Client;
import com.github.jlangch.aviron.dto.ScanResult;
import com.github.jlangch.aviron.events.FileWatchErrorEvent;
import com.github.jlangch.aviron.events.FileWatchEvent;
import com.github.jlangch.aviron.events.FileWatchRegisterEvent;
import com.github.jlangch.aviron.events.FileWatchTerminationEvent;
import com.github.jlangch.aviron.events.RealtimeScanEvent;


public class RealtimeScanner {

    public RealtimeScanner(
           final Client client,
           final Path mainDir,
           final List<Path> secondaryDirs,
           final Predicate<FileWatchEvent> scanApprover,
           final Consumer<RealtimeScanEvent> scanListener,
           final int sleepTimeOnIdle
    ) {
        if (client == null) {
            throw new IllegalArgumentException("A 'client' must not be null!");
        }
        if (mainDir == null) {
            throw new IllegalArgumentException("A 'mainDir' must not be null!");
        }
        if (!Files.isDirectory(mainDir)) {
            throw new IllegalArgumentException(
                    "The realtime scanner 'mainDir' is not an existing directory!");
        }

        this.client = client;
        this.mainDir = mainDir;
        if (secondaryDirs != null) {
            this.secondaryDirs.addAll(secondaryDirs);
        }
        this.scanApprover = scanApprover;
        this.scanListener = scanListener;
        this.sleepTimeOnIdle = Math.max(1, sleepTimeOnIdle);
    }


    public boolean isRunning() {
        return running.get();
    }

    public synchronized void start() {
        if (running.compareAndSet(false, true)) {
            // start realtime scanner

            try {
                fileWatcherQueue.set(new FileWatcherQueue(5_000));

                watcher.set(new FileWatcher(
                                    mainDir,
                                    this::fileWatchEventListener,
                                    this::registerEventListener,
                                    this::errorEventListener,
                                    this::terminationEventListener));
            }
            catch(Exception ex) {
                running.set(false);
                throw ex;
            }

            watcher.get().register(secondaryDirs);
            
            final Runnable runnable = () -> {
                while (running.get()) {
                    try {
                        final FileWatcherQueue queue = fileWatcherQueue.get();
                        if (queue != null) {
                            for(int ii=0; ii<BATCH_SIZE && running.get(); ii++) {
                                final File file = queue.pop();
                                if (file.isFile()) {
                                    final Path path = file.toPath();
                                    final ScanResult result = client.scan(path);
                                    if (scanListener != null) {
                                        safeRun(() -> scanListener.accept(
                                                        new RealtimeScanEvent(path, result)));
                                    }
                                }
                            }
                            
                            if (queue.isEmpty()) {
                                for(int ii=0; ii<sleepTimeOnIdle && running.get(); ii++) {
                                    sleep(1);
                                }
                            }
                        }
                        else {
                            sleep(2);
                        }
                    }
                    catch(Exception ex) {
                        // prevent thread spinning in fatal error conditions
                        sleep(2);
                    }
                }
            };

            final Thread thread = new Thread(runnable);
            thread.setDaemon(true);
            thread.setName("aviron-rtscan-" + threadCounter.getAndIncrement());
            thread.start();
        }
    }

    public synchronized void stop() {
        if (running.compareAndSet(true, false)) {
            // stop realtime scanner
            final FileWatcher fw = watcher.get();
            if (fw != null && fw.isRunning()) {
               fw.close();
            }
        }
    }


    private void fileWatchEventListener(final FileWatchEvent event) {      
        final FileWatcherQueue queue = fileWatcherQueue.get();

        switch(event.getType()) {
            case CREATED:
            case MODIFIED:
                try {
                    if (scanApprover == null || scanApprover.test(event)) {
                        queue.push(event.getPath().toFile());
                    }
                }
                catch(Exception ex) { }
                break;

            case DELETED:
                queue.remove(event.getPath().toFile());
                break;

            case OVERFLOW:
                break;

            default:
                break;
        }
    }

    private void registerEventListener(final FileWatchRegisterEvent event) {
        
    }

    private void errorEventListener(final FileWatchErrorEvent event) {
        
    }

    private void terminationEventListener(final FileWatchTerminationEvent event) {
        
    }

    private void sleep(int seconds) {
        try { 
            Thread.sleep(seconds * 1000L); 
        } 
        catch(Exception ex) { }
    }

    private void safeRun(final Runnable r) {
        try {
            r.run();
        }
        catch(Exception e) { }
    }


    private static final int BATCH_SIZE = 300;

    private static final AtomicLong threadCounter = new AtomicLong(1L);

    private final AtomicBoolean running = new AtomicBoolean(false);

    private final Client client;
    private final Path mainDir;
    private final List<Path> secondaryDirs = new ArrayList<>();
    private final Predicate<FileWatchEvent> scanApprover;
    private final Consumer<RealtimeScanEvent> scanListener;
    private final int sleepTimeOnIdle;

    private AtomicReference<FileWatcher> watcher = new AtomicReference<>();
    private AtomicReference<FileWatcherQueue> fileWatcherQueue = new AtomicReference<>();
}
