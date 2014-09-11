// Copyright 2014 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.devtools.build.lib.testutil;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.devtools.build.lib.blaze.BlazeDirectories;
import com.google.devtools.build.lib.util.SkyframeMode;
import com.google.devtools.build.lib.vfs.FileSystemUtils;
import com.google.devtools.build.lib.vfs.Path;
import com.google.devtools.build.lib.view.config.BinTools;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Some static utility functions for testing Blaze code. In contrast to {@link TestUtils}, these
 * functions are either Blaze- or google3-specific.
 */
public class BlazeTestUtils {
  /**
   * A list of all embedded binaries that go into the regular Blaze binary. This is used to
   * fake a list of these because the usual method of scanning the directory tree cannot be used,
   * since we don't have one in tests.
   */
  public static final ImmutableList<String> EMBEDDED_TOOLS = ImmutableList.of(
      "build-runfiles",
      "p4_client_info.sh",
      "grep-includes",
      "process-wrapper",
      "build_interface_so");

  private BlazeTestUtils() {}

  /** Returns the skyframe mode the test class should be run in. */
  public static SkyframeMode skyframeMode(Class<?> clazz) {
    String skyframeProperty = System.getProperty("blaze.skyframe");
    SkyframeMode skyframeMin = Suite.getSkyframeMin(clazz);
    SkyframeMode skyframeMax = Suite.getSkyframeMax(clazz);
    if (skyframeProperty == null) {
      // This most likely means the test is not being run via 'blaze test', e.g. it's being run in
      // an IDE without blaze integration, such as Intellij.
      return skyframeMin;
    }
    SkyframeMode skyframe = SkyframeMode.valueOf(skyframeProperty);
    if (!skyframe.atLeast(skyframeMin) || !skyframe.atMost(skyframeMax)) {
      // This most likely means the test is being run through an inappropriate blaze test target,
      // so we at least try to run with a sensible skyframe mode.
      return skyframeMin;
    }
    return skyframe;
  }

  /**
   * Populates the _embedded_binaries/ directory, containing all binaries/libraries, by symlinking
   * directories#getEmbeddedBinariesRoot() to the test's runfiles tree.
   */
  public static BinTools getIntegrationBinTools(BlazeDirectories directories) throws IOException {
    Path embeddedDir = directories.getEmbeddedBinariesRoot();
    FileSystemUtils.createDirectoryAndParents(embeddedDir);

    Path runfiles = directories.getFileSystem().getPath(BlazeTestUtils.runfilesDir());
    List<String> tools = new ArrayList<>();
    // Copy over everything in embedded_scripts.
    Path embeddedScripts = runfiles.getRelative("google3/devtools/blaze/embedded_scripts");
    Collection<Path> files = new ArrayList<Path>();
    if (embeddedScripts.exists()) {
      files.addAll(embeddedScripts.getDirectoryEntries());
    } else {
      System.err.println("test does not have " + embeddedScripts);
    }
    files.add(runfiles.getRelative("google3/util/elf/build_interface_so"));

    for (Path fromFile : files) {
      tools.add(fromFile.getBaseName());
      try {
        embeddedDir.getChild(fromFile.getBaseName()).createSymbolicLink(fromFile);
      } catch (IOException e) {
        System.err.println("Could not symlink: " + e.getMessage());
      }
    }

    return BinTools.forIntegrationTesting(
        directories, embeddedDir.toString(), BlazeTestUtils.EMBEDDED_TOOLS);
  }

  /**
   * Writes a FilesetRule to a String array.
   *
   * @param name the name of the rule.
   * @param out the output directory.
   * @param entries The FilesetEntry entries.
   * @return the String array of the rule.  One String for each line.
   */
  public static String[] createFilesetRule(String name, String out, String... entries) {
    return new String[] {
        String.format("Fileset(name = '%s', out = '%s',", name, out),
                      "        entries = [" +  Joiner.on(", ").join(entries) + "])"
    };
  }

  public static File undeclaredOutputDir() {
    String dir = System.getenv("TEST_UNDECLARED_OUTPUTS_DIR");
    if (dir != null) {
      return new File(dir);
    }

    return TestUtils.tmpDirFile();
  }

  public static String srcDir() {
    return runfilesDir();
  }

  public static String runfilesDir() {
    File runfilesDir;

    String runfilesDirStr = TestUtils.getUserValue("TEST_SRCDIR");
    if (runfilesDirStr != null && runfilesDirStr.length() > 0) {
      runfilesDir = new File(runfilesDirStr);
    } else {
      // Goal is to find the google3 directory, so we check current
      // directory, then keep backing up until we see google3.
      File dir = new File("");
      while (dir != null) {
        dir = dir.getAbsoluteFile();

        File google3 = new File(dir, "google3");
        if (google3.exists()) {
          return dir.getAbsolutePath();
        }

        dir = dir.getParentFile();
      }

      // Fallback default $CWD/.. works if CWD is //depot/google3
      runfilesDir = new File("").getAbsoluteFile().getParentFile();
    }

    return runfilesDir.getAbsolutePath();
  }

  /** Creates an empty file, along with all its parent directories. */
  public static void makeEmptyFile(Path path) throws IOException {
    FileSystemUtils.createDirectoryAndParents(path.getParentDirectory());
    FileSystemUtils.createEmptyFile(path);
  }

  /**
   * Changes the mtime of the file "path", which must exist.  No guarantee is
   * made about the new mtime except that it is different from the previous one.
   *
   * @throws IOException if the mtime could not be read or set.
   */
  public static void changeModtime(Path path)
    throws IOException {
    long prevMtime = path.getLastModifiedTime();
    long newMtime = prevMtime;
    do {
      newMtime += 1000;
      path.setLastModifiedTime(newMtime);
    } while (path.getLastModifiedTime() == prevMtime);
  }
}
