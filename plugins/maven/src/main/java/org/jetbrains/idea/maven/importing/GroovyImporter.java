package org.jetbrains.idea.maven.importing;

import com.intellij.util.PairConsumer;
import org.jdom.Element;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.utils.MavenJDOMUtil;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.util.List;

/**
 * This class can not be moved to org.jetbrains.idea.maven.plugins.groovy package because it's used from 'Eclipse Groovy Compiler Plugin'
 */
public abstract class GroovyImporter extends MavenImporter {
  public GroovyImporter(String pluginGroupID, String pluginArtifactID) {
    super(pluginGroupID, pluginArtifactID);
  }

  @Override
  public void collectSourceRoots(MavenProject mavenProject, PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    collectSourceOrTestFolders(mavenProject, "compile", "src/main/groovy", JavaSourceRootType.SOURCE, result);
    collectSourceOrTestFolders(mavenProject, "testCompile", "src/test/groovy", JavaSourceRootType.TEST_SOURCE, result);
  }

  private void collectSourceOrTestFolders(MavenProject mavenProject, String goal, String defaultDir, JavaSourceRootType type,
                                          PairConsumer<String, JpsModuleSourceRootType<?>> result) {
    Element sourcesElement = getGoalConfig(mavenProject, goal);
    List<String> dirs = MavenJDOMUtil.findChildrenValuesByPath(sourcesElement, "sources", "fileset.directory");
    if (dirs.isEmpty()) {
      result.consume(mavenProject.getDirectory() + "/" + defaultDir, type);
      return;
    }
    for (String dir : dirs) {
      result.consume(dir, type);
    }
  }

  @Override
  public void collectExcludedFolders(MavenProject mavenProject, List<String> result) {
    String stubsDir = findGoalConfigValue(mavenProject, "generateStubs", "outputDirectory");
    String testStubsDir = findGoalConfigValue(mavenProject, "generateTestStubs", "outputDirectory");

    // exclude common parent of /groovy-stubs/main and /groovy-stubs/test
    String defaultStubsDir = mavenProject.getGeneratedSourcesDirectory(false) + "/groovy-stubs";

    result.add(stubsDir == null ? defaultStubsDir : stubsDir);
    result.add(testStubsDir == null ? defaultStubsDir : testStubsDir);
  }
}
