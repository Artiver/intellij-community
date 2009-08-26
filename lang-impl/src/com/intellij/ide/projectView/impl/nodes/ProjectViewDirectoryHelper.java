/*
 * User: anna
 * Date: 23-Jan-2008
 */
package com.intellij.ide.projectView.impl.nodes;

import com.intellij.ide.projectView.ProjectViewNode;
import com.intellij.ide.projectView.ViewSettings;
import com.intellij.ide.util.treeView.AbstractTreeNode;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ProjectViewDirectoryHelper {
  protected static final Logger LOG = Logger.getInstance("#" + ProjectViewDirectoryHelper.class.getName());

  private final Project myProject;

  public static ProjectViewDirectoryHelper getInstance(Project project) {
    return ServiceManager.getService(project, ProjectViewDirectoryHelper.class);
  }

  public ProjectViewDirectoryHelper(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  @Nullable
  public String getLocationString(@NotNull PsiDirectory psiDirectory) {
    final VirtualFile directory = psiDirectory.getVirtualFile();
    final VirtualFile contentRootForFile = ProjectRootManager.getInstance(myProject)
      .getFileIndex().getContentRootForFile(directory);
    if (Comparing.equal(contentRootForFile, psiDirectory)) {
      return directory.getPresentableUrl();
    }
    return null;
  }



  public boolean isShowFQName(ViewSettings settings, Object parentValue, PsiDirectory value) {
    return false;
  }


  @Nullable
  public String getNodeName(ViewSettings settings, Object parentValue, PsiDirectory directory) {
    return directory.getName();
  }

  public boolean skipDirectory(PsiDirectory directory) {
    return true;
  }

  public boolean showFileInLibClasses(VirtualFile vFile) {
    return true;
  }

  public boolean isEmptyMiddleDirectory(PsiDirectory directory, final boolean strictlyEmpty) {
    return false;
  }

  public boolean supportsFlattenPackages() {
    return false;
  }

  public boolean supportsHideEmptyMiddlePackages() {
    return false;
  }

  public boolean canRepresent(Object element, PsiDirectory directory) {
    if (element instanceof VirtualFile) {
      VirtualFile vFile = (VirtualFile) element;
      return directory.getVirtualFile() == vFile;
    }
    return false;
  }

  public Collection<AbstractTreeNode> getDirectoryChildren(final PsiDirectory psiDirectory,
                                                           final ViewSettings settings,
                                                           final boolean withSubDirectories) {
    final List<AbstractTreeNode> children = new ArrayList<AbstractTreeNode>();
    final Project project = psiDirectory.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    final Module module = fileIndex.getModuleForFile(psiDirectory.getVirtualFile());
    final ModuleFileIndex moduleFileIndex = module == null ? null : ModuleRootManager.getInstance(module).getFileIndex();
    if (!settings.isFlattenPackages() || skipDirectory(psiDirectory)) {
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getChildren(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    else { // source directory in "flatten packages" mode
      final PsiDirectory parentDir = psiDirectory.getParentDirectory();
      if (parentDir == null || skipDirectory(parentDir) /*|| !rootDirectoryFound(parentDir)*/ && withSubDirectories) {
        addAllSubpackages(children, psiDirectory, moduleFileIndex, settings);
      }
      PsiDirectory[] subdirs = psiDirectory.getSubdirectories();
      for (PsiDirectory subdir : subdirs) {
        if (!skipDirectory(subdir)) {
          continue;
        }
        if (moduleFileIndex != null) {
          if (!moduleFileIndex.isInContent(subdir.getVirtualFile())) {
            continue;
          }
        }
        if (withSubDirectories) {
          children.add(new PsiDirectoryNode(project, subdir, settings));
        }
      }
      processPsiDirectoryChildren(psiDirectory, psiDirectory.getFiles(), children, fileIndex, moduleFileIndex, settings,
                                  withSubDirectories);
    }
    return children;
  }

  // used only for non-flatten packages mode
  public  void processPsiDirectoryChildren(final PsiDirectory psiDir,
                                                  PsiElement[] children,
                                                  List<AbstractTreeNode> container,
                                                  ProjectFileIndex projectFileIndex,
                                                  ModuleFileIndex moduleFileIndex,
                                                  ViewSettings viewSettings,
                                                  boolean withSubDirectories) {
    for (PsiElement child : children) {
      LOG.assertTrue(child.isValid());

      final VirtualFile vFile;
      if (child instanceof PsiFile) {
        vFile = ((PsiFile)child).getVirtualFile();
        addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiFileNode.class, child, viewSettings);
      }
      else if (child instanceof PsiDirectory) {
        if (withSubDirectories) {
          PsiDirectory dir = (PsiDirectory)child;
          vFile = dir.getVirtualFile();
          if (!vFile.equals(projectFileIndex.getSourceRootForFile(vFile))) { // if is not a source root
            if (viewSettings.isHideEmptyMiddlePackages() && !skipDirectory(psiDir) && isEmptyMiddleDirectory(dir, true)) {
              processPsiDirectoryChildren(dir, dir.getChildren(), container, projectFileIndex, moduleFileIndex, viewSettings,
                                          withSubDirectories); // expand it recursively
              continue;
            }
          }
          addNode(moduleFileIndex, projectFileIndex, psiDir, vFile, container, PsiDirectoryNode.class, child, viewSettings);
        }
      }
      else {
        LOG.assertTrue(false, "Either PsiFile or PsiDirectory expected as a child of " + child.getParent() + ", but was " + child);
      }
    }
  }

  public void addNode(ModuleFileIndex moduleFileIndex,
                              ProjectFileIndex projectFileIndex,
                              PsiDirectory psiDir,
                              VirtualFile vFile,
                              List<AbstractTreeNode> container,
                              Class<? extends AbstractTreeNode> nodeClass,
                              PsiElement element,
                              final ViewSettings settings) {
    if (vFile == null) {
      return;
    }
    // this check makes sense for classes not in library content only
    if (moduleFileIndex != null && !moduleFileIndex.isInContent(vFile)) {
      return;
    }
    final boolean childInLibraryClasses = projectFileIndex.isInLibraryClasses(vFile);
    if (!projectFileIndex.isInSourceContent(vFile)) {
      if (childInLibraryClasses) {
        final VirtualFile psiDirVFile = psiDir.getVirtualFile();
        final boolean parentInLibraryContent =
          projectFileIndex.isInLibraryClasses(psiDirVFile) || projectFileIndex.isInLibrarySource(psiDirVFile);
        if (!parentInLibraryContent) {
          return;
        }
      }
    }
    if (childInLibraryClasses && !projectFileIndex.isInContent(vFile) && !showFileInLibClasses(vFile)) {
      return; // skip java sources in classpath
    }

    try {
      container.add(ProjectViewNode.createTreeNode(nodeClass, element.getProject(), element, settings));
    }
    catch (Exception e) {
      LOG.error(e);
    }
  }

  // used only in flatten packages mode
  public void addAllSubpackages(List<AbstractTreeNode> container,
                                        PsiDirectory dir,
                                        ModuleFileIndex moduleFileIndex,
                                        ViewSettings viewSettings) {
    final Project project = dir.getProject();
    PsiDirectory[] subdirs = dir.getSubdirectories();
    for (PsiDirectory subdir : subdirs) {
      if (skipDirectory(subdir)) {
        continue;
      }
      if (moduleFileIndex != null) {
        if (!moduleFileIndex.isInContent(subdir.getVirtualFile())) {
          continue;
        }
      }
      if (viewSettings.isHideEmptyMiddlePackages()) {
        if (!isEmptyMiddleDirectory(subdir, false)) {

          container.add(new PsiDirectoryNode(project, subdir, viewSettings));
        }
      }
      else {
        container.add(new PsiDirectoryNode(project, subdir, viewSettings));
      }
      addAllSubpackages(container, subdir, moduleFileIndex, viewSettings);
    }
  }
}