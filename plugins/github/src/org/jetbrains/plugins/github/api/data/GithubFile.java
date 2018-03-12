/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubFile {
  @Mandatory private String filename;

  @Mandatory private Integer additions;
  @Mandatory private Integer deletions;
  @Mandatory private Integer changes;
  @Mandatory private String status;
  @Mandatory private String rawUrl;
  private String blobUrl;
  private String patch;

  @NotNull
  public String getFilename() {
    return filename;
  }

  public int getAdditions() {
    return additions;
  }

  public int getDeletions() {
    return deletions;
  }

  public int getChanges() {
    return changes;
  }

  @NotNull
  public String getStatus() {
    return status;
  }

  @NotNull
  public String getRawUrl() {
    return rawUrl;
  }

  @Nullable
  public String getPatch() {
    return patch;
  }
}
