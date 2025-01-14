// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions.searcheverywhere;

import com.intellij.ui.CellRendererPanel;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.SeparatorComponent;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;

class GroupedListFactory extends SEResultsListFactory {
  @Override
  public SearchListModel createModel() {
    return new GroupedSearchListModel();
  }

  @Override
  public JBList<Object> createList(SearchListModel model) {
    return new JBList<>(model);
  }

  @Override
  ListCellRenderer<Object> createListRenderer(SearchListModel model, SearchEverywhereHeader header) {
    GroupedSearchListModel groupedModel = (GroupedSearchListModel)model;
    return new ListCellRenderer<>() {

      private final Map<String, ListCellRenderer<? super Object>> myRenderersCache = new HashMap<>();

      @Override
      public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        if (value == SearchListModel.MORE_ELEMENT) {
          return getMoreElementRenderer(list, index, isSelected, cellHasFocus);
        }

        Component component = getNonMoreElementRenderer(list, value, index, isSelected, cellHasFocus, groupedModel, myRenderersCache);

        if (!header.getSelectedTab().isSingleContributor() && groupedModel.isGroupFirstItem(index)) {
          SearchEverywhereContributor<Object> contributor = groupedModel.getContributorForIndex(index);
          //noinspection ConstantConditions
          component = myGroupTitleRenderer.withDisplayedData(contributor.getFullGroupName(), component);
        }

        return component;
      }
    };
  }

  private final GroupTitleRenderer myGroupTitleRenderer = new GroupTitleRenderer();

  private static class GroupTitleRenderer extends CellRendererPanel {

    private final SimpleColoredComponent titleLabel = new SimpleColoredComponent();

    GroupTitleRenderer() {
      setLayout(new BorderLayout());
      SeparatorComponent separatorComponent = new SeparatorComponent(
        titleLabel.getPreferredSize().height / 2, JBUI.CurrentTheme.BigPopup.listSeparatorColor(), null);

      JPanel topPanel = JBUI.Panels.simplePanel(5, 0)
        .addToCenter(separatorComponent)
        .addToLeft(titleLabel)
        .withBorder(JBUI.Borders.empty(1, 7))
        .withBackground(ExperimentalUI.isNewUI() ? JBUI.CurrentTheme.Popup.BACKGROUND : UIUtil.getListBackground());
      add(topPanel, BorderLayout.NORTH);
    }

    public GroupTitleRenderer withDisplayedData(@Nls String title, Component itemContent) {
      titleLabel.clear();
      titleLabel.append(title, SMALL_LABEL_ATTRS);
      Component prevContent = ((BorderLayout)getLayout()).getLayoutComponent(BorderLayout.CENTER);
      if (prevContent != null) {
        remove(prevContent);
      }
      add(itemContent, BorderLayout.CENTER);
      accessibleContext = itemContent.getAccessibleContext();

      return this;
    }
  }
}
