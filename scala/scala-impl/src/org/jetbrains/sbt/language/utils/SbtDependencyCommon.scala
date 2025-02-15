package org.jetbrains.sbt.language.utils

import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import org.jetbrains.sbt.project.SbtProjectSystem

object SbtDependencyCommon {
  val libScopes = "Compile,Provided,Test"
  val defaultLibScope = "Compile"
  val scopeTerminology = "Configuration"

  def refreshSbtProject(project: Project): Unit = {
    FileDocumentManager.getInstance.saveAllDocuments()
    ExternalSystemUtil.refreshProjects(new ImportSpecBuilder(project, SbtProjectSystem.Id))
  }
}
