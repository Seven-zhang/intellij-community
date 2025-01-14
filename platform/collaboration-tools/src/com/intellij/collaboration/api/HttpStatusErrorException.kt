// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api

import com.intellij.collaboration.messages.CollaborationToolsBundle
import org.jetbrains.annotations.Nls

class HttpStatusErrorException(val method: String,
                               val uri: String,
                               val statusCode: Int,
                               val body: String?)
  : RuntimeException("HTTP Request ${method} ${uri} failed with status code ${statusCode} and response body: ${body}") {

  @Nls
  override fun getLocalizedMessage(): String =
    CollaborationToolsBundle.message("http.status.error", method, uri, statusCode.toString(), body)
}