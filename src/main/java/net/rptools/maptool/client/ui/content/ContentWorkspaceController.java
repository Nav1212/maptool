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

import java.awt.EventQueue;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.swing.JOptionPane;
import net.rptools.maptool.client.MapTool;
import net.rptools.maptool.client.MapToolClient;
import net.rptools.maptool.content.ContentStore;
import net.rptools.maptool.content.ContentStore.CampaignRecord;
import net.rptools.maptool.content.ContentStore.Viewer;
import net.rptools.maptool.model.Token;
import net.rptools.maptool.model.Zone;
import net.rptools.maptool.server.proto.CampaignContentCommandMsg;
import net.rptools.maptool.server.proto.CampaignContentSnapshotMsg;
import net.rptools.maptool.server.proto.Message;

/** Campaign UI bridge. Only accepted server snapshots replace the locally persisted content. */
public final class ContentWorkspaceController {
  private static ContentWorkspaceDialog dialog;
  private static MapToolClient dialogClient;
  private static ContentStore editingStore;
  private static String dialogCampaignId;
  private static boolean updatePending;
  private static String pendingRequestId;

  private record PendingActor(CampaignRecord record, String mapId) {}

  private static final Map<String, PendingActor> pendingActors = new LinkedHashMap<>();

  private ContentWorkspaceController() {}

  public static void open() {
    var client = MapTool.getClient();
    if (client == null || client.getState() != MapToolClient.State.Connected) {
      MapTool.showError("Connect to a session before opening Campaign content.");
      return;
    }
    var campaign = client.getCampaign();
    var id = campaign.getId().toString();
    if (dialog != null
        && dialog.isDisplayable()
        && client == dialogClient
        && id.equals(dialogCampaignId)) {
      dialog.setVisible(true);
      dialog.toFront();
      requestSnapshot();
      return;
    }
    if (dialog != null) dialog.dispose();
    dialogCampaignId = id;
    dialogClient = client;
    pendingActors.clear();
    updatePending = false;
    pendingRequestId = null;
    editingStore = ContentStore.fromJson(campaign.getCampaignContentJson());
    var viewer =
        new Viewer(
            client.getPlayer().getName().toLowerCase(Locale.ROOT), client.getPlayer().isGM());
    dialog =
        new ContentWorkspaceDialog(
            MapTool.getFrame(),
            editingStore,
            viewer,
            ContentWorkspaceController::save,
            ContentWorkspaceController::queueActor);
    dialog.setRefreshSnapshotAction(ContentWorkspaceController::requestSnapshot);
    if (viewer.gm())
      dialog.setReloadCompendiumsAction(
          () -> {
            ContentPackageCatalog.reload(editingStore);
            save(editingStore.toJson());
          });
    dialog.setVisible(true);
    requestSnapshot();
  }

  private static void save(String json) {
    if (updatePending)
      throw new IllegalStateException("A content update is pending; wait for its result.");
    var client = MapTool.getClient();
    if (client == null
        || client != dialogClient
        || client.getState() != MapToolClient.State.Connected
        || !client.getPlayer().isGM()
        || !client.getCampaign().getId().toString().equals(dialogCampaignId)) {
      throw new IllegalStateException("The session changed; reopen Campaign content.");
    }
    pendingRequestId = java.util.UUID.randomUUID().toString();
    var command =
        CampaignContentCommandMsg.newBuilder()
            .setCampaignId(dialogCampaignId)
            .setUpdate(true)
            .setExpectedRevision(client.getCampaign().getCampaignContentRevision())
            .setJson(json)
            .setRequestId(pendingRequestId);
    updatePending = true;
    dialog.setWritePending(true);
    try {
      client
          .getConnection()
          .sendMessage(Message.newBuilder().setCampaignContentCommandMsg(command).build());
    } catch (RuntimeException failure) {
      updatePending = false;
      pendingRequestId = null;
      dialog.setWritePending(false);
      throw failure;
    }
  }

  public static void requestSnapshot() {
    var client = MapTool.getClient();
    if (client == null || client.getState() != MapToolClient.State.Connected) return;
    var command =
        CampaignContentCommandMsg.newBuilder()
            .setCampaignId(client.getCampaign().getId().toString());
    if (updatePending && pendingRequestId != null) {
      command.setRequestId("reconcile:" + pendingRequestId);
    }
    client
        .getConnection()
        .sendMessage(Message.newBuilder().setCampaignContentCommandMsg(command).build());
  }

  public static void receive(MapToolClient client, CampaignContentSnapshotMsg snapshot) {
    if (client != MapTool.getClient()) return;
    EventQueue.invokeLater(
        () -> {
          if (client != MapTool.getClient()) return;
          var campaign = client.getCampaign();
          if (!campaign.getId().toString().equals(snapshot.getCampaignId())
              || snapshot.getRevision() < campaign.getCampaignContentRevision()) return;
          try {
            campaign.installCampaignContentSnapshot(snapshot.getJson(), snapshot.getRevision());
            if (dialog != null
                && dialog.isDisplayable()
                && client == dialogClient
                && snapshot.getCampaignId().equals(dialogCampaignId)) {
              boolean recovered =
                  updatePending && snapshot.getRequestId().equals("reconcile:" + pendingRequestId);
              if (updatePending && !snapshot.getRequestId().equals(pendingRequestId) && !recovered)
                return;
              editingStore.replaceFromJson(snapshot.getJson());
              updatePending = false;
              pendingRequestId = null;
              dialog.setWritePending(false);
              dialog.refresh();
              if (snapshot.getError().isBlank()) {
                if (recovered) {
                  boolean actorUncertain = !pendingActors.isEmpty();
                  pendingActors.clear();
                  JOptionPane.showMessageDialog(
                      dialog,
                      actorUncertain
                          ? "Latest content loaded. Actor creation was not confirmed; check the map"
                              + " before importing again."
                          : "Latest content loaded. Check your last edit before retrying it.",
                      "Campaign content",
                      JOptionPane.INFORMATION_MESSAGE);
                  return;
                }
                for (var intent : java.util.List.copyOf(pendingActors.values())) {
                  var copy = intent.record();
                  var accepted =
                      editingStore.campaignRecords(new Viewer("gm", true)).stream()
                          .anyMatch(record -> record.id().equals(copy.id()));
                  pendingActors.remove(copy.id());
                  if (accepted && client.getPlayer().isGM()) {
                    try {
                      importActor(intent);
                    } catch (RuntimeException failure) {
                      MapTool.showError(
                          "The record was copied, but its actor import failed.", failure);
                    }
                  }
                }
              } else {
                pendingActors.clear();
                JOptionPane.showMessageDialog(
                    dialog, snapshot.getError(), "Campaign content", JOptionPane.WARNING_MESSAGE);
              }
            }
          } catch (RuntimeException failure) {
            MapTool.showError("Could not apply the campaign content snapshot.", failure);
          }
        });
  }

  private static void queueActor(CampaignRecord copy) {
    if (MapTool.getClient() != dialogClient)
      throw new IllegalStateException("The session changed; reopen Campaign content.");
    var renderer = MapTool.getFrame().getCurrentZoneRenderer();
    if (renderer == null)
      throw new IllegalArgumentException("Select a map before importing an actor.");
    pendingActors.put(copy.id(), new PendingActor(copy, renderer.getZone().getId().toString()));
  }

  private static void importActor(PendingActor intent) {
    if (MapTool.getClient() != dialogClient)
      throw new IllegalStateException("The session changed; reopen Campaign content.");
    var zone =
        MapTool.getClient()
            .getCampaign()
            .getZone(net.rptools.maptool.model.GUID.valueOf(intent.mapId()));
    if (zone == null)
      throw new IllegalArgumentException("The actor's target map no longer exists.");
    var copy = intent.record();
    var token = new Token(copy.value().name(), null);
    token.setLayer(Zone.Layer.TOKEN);
    token.setNotes(copy.value().description());
    var properties = copy.value().systemData().getAsJsonObject("properties");
    if (properties != null)
      properties
          .entrySet()
          .forEach(
              entry -> {
                if (entry.getValue().isJsonPrimitive())
                  token.setProperty(entry.getKey(), entry.getValue().getAsString());
              });
    token.setProperty("compendium.source", copy.sourceReference().toString());
    token.setProperty("compendium.record", copy.id());
    MapTool.serverCommand().putToken(zone.getId(), token);
  }
}
