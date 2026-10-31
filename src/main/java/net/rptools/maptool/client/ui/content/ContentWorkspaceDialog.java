/*
 * This software Copyright by the RPTools.net development team, and
 * licensed under the Affero GPL Version 3 or, at your option, any later
 * version.
 *
 * MapTool Source Code is distributed in the hope that it will be
 * useful, but WITHOUT ANY WARRANTY; without even the implied warranty
 * of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * You should have received a copy of the GNU Affero General Public
 * License * along with this source Code.  If not, please visit
 * <https://www.gnu.org/licenses/> and specifically the Affero license
 * text at <https://www.gnu.org/licenses/agpl.html>.
 */
package net.rptools.maptool.client.ui.content;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.Window;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.HyperlinkEvent;
import net.rptools.maptool.content.ContentStore;
import net.rptools.maptool.content.ContentStore.CampaignRecord;
import net.rptools.maptool.content.ContentStore.CompendiumRecord;
import net.rptools.maptool.content.ContentStore.JournalEntry;
import net.rptools.maptool.content.ContentStore.MapPin;
import net.rptools.maptool.content.ContentStore.PinVisibility;
import net.rptools.maptool.content.ContentStore.Viewer;

/**
 * Reusable campaign content UI. The host supplies persistence and permitted actor import actions.
 */
public final class ContentWorkspaceDialog extends JDialog {
  private final ContentStore store;
  private final Viewer viewer;
  private final Consumer<String> persistJson;
  private final Consumer<CampaignRecord> importActor;
  private final Predicate<MapPin> visibleArea;
  private final DefaultListModel<CompendiumRecord> records = new DefaultListModel<>();
  private final DefaultListModel<JournalEntry> journals = new DefaultListModel<>();
  private final DefaultListModel<MapPin> pins = new DefaultListModel<>();
  private final JList<CompendiumRecord> recordList = list(records);
  private final JList<JournalEntry> journalList = list(journals);
  private final JList<MapPin> pinList = list(pins);
  private final JTextField recordSearch = new JTextField();
  private final JTextField recordKind = new JTextField();
  private final JTextField recordTags = new JTextField();
  private final JTextField journalSearch = new JTextField();
  private final JEditorPane recordPreview = preview();
  private final JEditorPane journalPreview = preview();
  private final JEditorPane pinPreview = preview();
  private final Parser markdown = Parser.builder().build();
  private final HtmlRenderer renderer = HtmlRenderer.builder().escapeHtml(true).build();
  private final java.util.List<JButton> writeButtons = new java.util.ArrayList<>();
  private Runnable reloadCompendiums;
  private JButton reloadButton;
  private Runnable refreshSnapshot;

  public ContentWorkspaceDialog(
      Window owner,
      ContentStore store,
      Viewer viewer,
      Consumer<String> persistJson,
      Consumer<CampaignRecord> importActor) {
    this(owner, store, viewer, persistJson, importActor, pin -> false);
  }

  /** A player pin visibility predicate must be evaluated by the authoritative map host. */
  public ContentWorkspaceDialog(
      Window owner,
      ContentStore store,
      Viewer viewer,
      Consumer<String> persistJson,
      Consumer<CampaignRecord> importActor,
      Predicate<MapPin> visibleArea) {
    super(owner, "Campaign content", ModalityType.MODELESS);
    this.store = java.util.Objects.requireNonNull(store);
    this.viewer = java.util.Objects.requireNonNull(viewer);
    this.persistJson = java.util.Objects.requireNonNull(persistJson);
    this.importActor = java.util.Objects.requireNonNull(importActor);
    this.visibleArea = java.util.Objects.requireNonNull(visibleArea);
    var tabs = new JTabbedPane();
    tabs.addTab("Compendiums", compendiumPanel());
    tabs.addTab("Journals", journalsPanel());
    tabs.addTab("Map pins", pinsPanel());
    setContentPane(tabs);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    setPreferredSize(new Dimension(950, 650));
    pack();
    setLocationRelativeTo(owner);
    refresh();
  }

  public void refresh() {
    var oldRecord = recordList.getSelectedValue();
    var oldJournal = journalList.getSelectedValue();
    var oldPin = pinList.getSelectedValue();
    records.clear();
    store
        .searchCompendium(recordSearch.getText(), recordKind.getText(), csv(recordTags.getText()))
        .forEach(records::addElement);
    journals.clear();
    store.searchJournals(viewer, journalSearch.getText()).forEach(journals::addElement);
    pins.clear();
    store.pins(viewer, null, visibleArea).forEach(pins::addElement);
    restoreSelection(recordList, oldRecord);
    restoreSelection(journalList, oldJournal);
    restoreSelection(pinList, oldPin);
  }

  public void setWritePending(boolean pending) {
    writeButtons.forEach(button -> button.setEnabled(!pending));
    if (reloadButton != null) reloadButton.setEnabled(!pending && reloadCompendiums != null);
  }

  public void setReloadCompendiumsAction(Runnable action) {
    reloadCompendiums = java.util.Objects.requireNonNull(action);
    if (reloadButton != null) reloadButton.setEnabled(true);
  }

  public void setRefreshSnapshotAction(Runnable action) {
    refreshSnapshot = java.util.Objects.requireNonNull(action);
  }

  private JPanel compendiumPanel() {
    var panel = new JPanel(new BorderLayout(8, 8));
    var filters = new JPanel(new GridLayout(2, 3, 6, 3));
    filters.add(new JLabel("Search text"));
    filters.add(new JLabel("Kind (optional)"));
    filters.add(new JLabel("Tags (comma separated)"));
    filters.add(recordSearch);
    filters.add(recordKind);
    filters.add(recordTags);
    panel.add(filters, BorderLayout.NORTH);
    for (var field : List.of(recordSearch, recordKind, recordTags))
      field.addActionListener(e -> refresh());
    recordList.setCellRenderer(
        renderer(
            value -> {
              var record = (CompendiumRecord) value;
              return record.name() + "  [" + record.kind() + "] — " + record.reference();
            }));
    recordList.addListSelectionListener(
        e -> {
          var record = recordList.getSelectedValue();
          if (record == null) {
            recordPreview.setText("");
            return;
          }
          showMarkdown(
              recordPreview,
              "# "
                  + record.name()
                  + "\n\n"
                  + record.description()
                  + "\n\nSource: "
                  + record.source()
                  + "\n\nLicense: "
                  + record.license()
                  + "\n\nAssets: "
                  + String.join(", ", record.assetIds()));
        });
    panel.add(split(recordList, recordPreview), BorderLayout.CENTER);
    var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
    buttons.add(button("Search", this::refresh));
    buttons.add(
        button(
            "Refresh content",
            () -> {
              if (refreshSnapshot != null) refreshSnapshot.run();
              else refresh();
            }));
    if (viewer.gm()) {
      reloadButton =
          button(
              "Reload add-on compendiums",
              () -> {
                if (reloadCompendiums != null) {
                  reloadCompendiums.run();
                  refresh();
                }
              });
      reloadButton.setEnabled(false);
      buttons.add(reloadButton);
      buttons.add(button("Copy to campaign", () -> importRecord(false)));
      buttons.add(button("Import actor", () -> importRecord(true)));
    }
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private void importRecord(boolean actor) {
    var selected = recordList.getSelectedValue();
    if (selected == null) throw new IllegalArgumentException("Select a compendium record first");
    if (actor && !selected.kind().equalsIgnoreCase("actor")) {
      throw new IllegalArgumentException("Select an actor record to create a token");
    }
    var copy = store.copyToCampaign(viewer, selected.reference(), UUID.randomUUID().toString());
    persistJson.accept(store.toJson());
    if (actor) importActor.accept(copy);
    JOptionPane.showMessageDialog(
        this, "Copy requested. Use Refresh content if confirmation is delayed.");
  }

  private JPanel journalsPanel() {
    var panel = new JPanel(new BorderLayout(8, 8));
    panel.add(journalSearch, BorderLayout.NORTH);
    journalSearch.setToolTipText("Search journal title, folder, tags, and text; press Enter");
    journalSearch.addActionListener(e -> refresh());
    journalList.setCellRenderer(
        renderer(
            value -> {
              var entry = (JournalEntry) value;
              return (entry.folder().isBlank() ? "" : entry.folder() + "/") + entry.title();
            }));
    journalList.addListSelectionListener(
        e -> showJournal(journalList.getSelectedValue(), journalPreview));
    journalPreview.addHyperlinkListener(
        e -> {
          if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED
              && e.getDescription().startsWith("journal:")) {
            var entry = store.journal(viewer, e.getDescription().substring("journal:".length()));
            if (entry != null) {
              journalList.setSelectedValue(entry, true);
              showJournal(entry, journalPreview);
            } else JOptionPane.showMessageDialog(this, "This journal link is unavailable.");
          }
        });
    panel.add(split(journalList, journalPreview), BorderLayout.CENTER);
    var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
    buttons.add(button("Search", this::refresh));
    if (viewer.gm()) {
      buttons.add(button("New entry", () -> editJournal(null)));
      buttons.add(button("Edit / sharing", () -> editJournal(requireJournal())));
      buttons.add(
          button(
              "Delete",
              () -> {
                var entry = requireJournal();
                if (JOptionPane.showConfirmDialog(
                        this,
                        "Delete journal '"
                            + entry.title()
                            + "'? Map pins remain available for GM repair.",
                        "Delete journal",
                        JOptionPane.OK_CANCEL_OPTION)
                    == JOptionPane.OK_OPTION) {
                  store.deleteJournal(viewer, entry.id());
                  changed();
                }
              }));
    }
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private void editJournal(JournalEntry old) {
    var title = new JTextField(old == null ? "" : old.title());
    var folder = new JTextField(old == null ? "" : old.folder());
    var tags = new JTextField(old == null ? "" : String.join(", ", old.tags()));
    var assets = new JTextField(old == null ? "" : String.join(", ", old.assetIds()));
    var links = new JTextField(old == null ? "" : String.join(", ", old.links()));
    var audience = new JTextField(old == null ? "" : String.join(", ", old.sharedWith()));
    var all = new JCheckBox("Share with all players", old != null && old.sharedWithAll());
    var body = new JTextArea(old == null ? "" : old.markdown(), 14, 55);
    var metadata = new JPanel(new GridLayout(0, 2, 6, 5));
    metadata.add(new JLabel("Title"));
    metadata.add(title);
    metadata.add(new JLabel("Folder"));
    metadata.add(folder);
    metadata.add(new JLabel("Tags (comma separated)"));
    metadata.add(tags);
    metadata.add(new JLabel("Asset IDs (comma separated)"));
    metadata.add(assets);
    metadata.add(new JLabel("Linked journal IDs"));
    metadata.add(links);
    metadata.add(new JLabel("Share with player IDs (empty = GM only)"));
    metadata.add(audience);
    metadata.add(all);
    metadata.add(new JLabel("Body links: [label](journal:entry-id)"));
    var editor = new JPanel(new BorderLayout(8, 8));
    editor.add(metadata, BorderLayout.NORTH);
    editor.add(new JScrollPane(body), BorderLayout.CENTER);
    if (JOptionPane.showConfirmDialog(
            this,
            editor,
            old == null ? "New journal" : "Edit journal",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      if (old != null && !old.equals(store.journal(viewer, old.id()))) {
        throw new IllegalStateException(
            "This journal changed while you were editing; reopen the latest version.");
      }
      store.putJournal(
          viewer,
          new JournalEntry(
              old == null ? UUID.randomUUID().toString() : old.id(),
              title.getText(),
              folder.getText(),
              csv(tags.getText()),
              body.getText(),
              csv(assets.getText()),
              csv(links.getText()),
              all.isSelected(),
              csv(audience.getText())));
      changed();
    }
  }

  private JPanel pinsPanel() {
    var panel = new JPanel(new BorderLayout(8, 8));
    pinList.setCellRenderer(
        renderer(
            value -> {
              var pin = (MapPin) value;
              return pin.label() + " — " + pin.mapId() + " (" + pin.x() + ", " + pin.y() + ")";
            }));
    pinList.addListSelectionListener(
        e -> {
          var pin = pinList.getSelectedValue();
          if (pin == null) pinPreview.setText("");
          else showJournal(store.journal(viewer, pin.journalId()), pinPreview);
        });
    panel.add(split(pinList, pinPreview), BorderLayout.CENTER);
    var buttons = new JPanel(new FlowLayout(FlowLayout.LEADING));
    if (viewer.gm()) {
      buttons.add(button("New pin", () -> editPin(null)));
      buttons.add(button("Edit / move / relink", () -> editPin(requirePin())));
      buttons.add(
          button(
              "Delete pin",
              () -> {
                store.deletePin(viewer, requirePin().id());
                changed();
              }));
    }
    panel.add(buttons, BorderLayout.SOUTH);
    return panel;
  }

  private void editPin(MapPin old) {
    var label = new JTextField(old == null ? "" : old.label());
    var map = new JTextField(old == null ? "" : old.mapId());
    var journal = new JTextField(old == null ? "" : old.journalId());
    var x = new JTextField(old == null ? "0" : String.valueOf(old.x()));
    var y = new JTextField(old == null ? "0" : String.valueOf(old.y()));
    var visibility = new JComboBox<>(PinVisibility.values());
    visibility.setSelectedItem(old == null ? PinVisibility.GM_ONLY : old.visibility());
    var editor = new JPanel(new GridLayout(0, 2, 6, 5));
    editor.add(new JLabel("Label"));
    editor.add(label);
    editor.add(new JLabel("Map ID"));
    editor.add(map);
    editor.add(new JLabel("Journal ID"));
    editor.add(journal);
    editor.add(new JLabel("Map X"));
    editor.add(x);
    editor.add(new JLabel("Map Y"));
    editor.add(y);
    editor.add(new JLabel("Visibility"));
    editor.add(visibility);
    if (JOptionPane.showConfirmDialog(
            this, editor, "Map pin", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE)
        == JOptionPane.OK_OPTION) {
      if (old != null && !store.pins(viewer, null, visibleArea).contains(old)) {
        throw new IllegalStateException(
            "This pin changed while you were editing; reopen the latest version.");
      }
      store.putPin(
          viewer,
          new MapPin(
              old == null ? UUID.randomUUID().toString() : old.id(),
              map.getText(),
              journal.getText(),
              label.getText(),
              Double.parseDouble(x.getText()),
              Double.parseDouble(y.getText()),
              (PinVisibility) visibility.getSelectedItem()));
      changed();
    }
  }

  private JournalEntry requireJournal() {
    var entry = journalList.getSelectedValue();
    if (entry == null) throw new IllegalArgumentException("Select a journal first");
    return entry;
  }

  private MapPin requirePin() {
    var pin = pinList.getSelectedValue();
    if (pin == null) throw new IllegalArgumentException("Select a map pin first");
    return pin;
  }

  private void showJournal(JournalEntry entry, JEditorPane pane) {
    if (entry == null) {
      pane.setText("This journal entry is unavailable; the GM can repair its link.");
      return;
    }
    showMarkdown(
        pane,
        "# "
            + entry.title()
            + "\n\n"
            + entry.markdown()
            + "\n\nEntry ID: "
            + entry.id()
            + "\n\nAssets: "
            + String.join(", ", entry.assetIds()));
  }

  private void showMarkdown(JEditorPane pane, String text) {
    // Do not fetch external images while previewing imported descriptions.
    pane.setText(
        "<html><body>"
            + renderer.render(markdown.parse(text)).replaceAll("(?is)<img\\b[^>]*>", "[image]")
            + "</body></html>");
    pane.setCaretPosition(0);
  }

  private void changed() {
    persistJson.accept(store.toJson());
    refresh();
  }

  private JButton button(String label, Runnable action) {
    var button = new JButton(label);
    if (viewer.gm() && !label.equals("Search") && !label.equals("Refresh content"))
      writeButtons.add(button);
    button.addActionListener(
        e -> {
          try {
            action.run();
          } catch (RuntimeException failure) {
            JOptionPane.showMessageDialog(
                this, failure.getMessage(), label, JOptionPane.ERROR_MESSAGE);
          }
        });
    return button;
  }

  private static <T> JList<T> list(DefaultListModel<T> model) {
    var list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    return list;
  }

  private static JEditorPane preview() {
    var pane = new JEditorPane("text/html", "");
    pane.setEditable(false);
    return pane;
  }

  private static JSplitPane split(JList<?> list, JEditorPane pane) {
    var split =
        new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, new JScrollPane(list), new JScrollPane(pane));
    split.setResizeWeight(0.35);
    return split;
  }

  private static Set<String> csv(String text) {
    var values = new LinkedHashSet<String>();
    Arrays.stream(text.split(",")).map(String::trim).filter(s -> !s.isEmpty()).forEach(values::add);
    return values;
  }

  private static <T> void restoreSelection(JList<T> list, T previous) {
    if (previous != null) list.setSelectedValue(previous, true);
    if (list.getSelectedIndex() < 0 && list.getModel().getSize() > 0) list.setSelectedIndex(0);
  }

  private static DefaultListCellRenderer renderer(
      java.util.function.Function<Object, String> text) {
    return new DefaultListCellRenderer() {
      @Override
      public java.awt.Component getListCellRendererComponent(
          JList<?> list, Object value, int index, boolean selected, boolean focus) {
        putClientProperty("html.disable", Boolean.TRUE);
        return super.getListCellRendererComponent(
            list, value == null ? "" : text.apply(value), index, selected, focus);
      }
    };
  }
}
