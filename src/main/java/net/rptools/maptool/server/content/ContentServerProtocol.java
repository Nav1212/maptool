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
package net.rptools.maptool.server.content;

import java.awt.EventQueue;
import java.util.Locale;
import net.rptools.maptool.content.ContentStore;
import net.rptools.maptool.content.ContentStore.Viewer;
import net.rptools.maptool.model.player.Player;
import net.rptools.maptool.server.MapToolServer;
import net.rptools.maptool.server.proto.CampaignContentCommandMsg;
import net.rptools.maptool.server.proto.CampaignContentSnapshotMsg;
import net.rptools.maptool.server.proto.Message;

/** Targeted campaign-content synchronization; sender roles come only from installed connections. */
public final class ContentServerProtocol {
  private ContentServerProtocol() {}

  public static void handle(
      MapToolServer server, String connectionId, CampaignContentCommandMsg command) {
    // Preserve ordering with existing campaign replacement handlers on the Swing event queue.
    EventQueue.invokeLater(
        () -> {
          var sender = server.getPlayerForConnection(connectionId);
          if (sender == null) return;
          var campaign = server.getCampaign();
          if (!campaign.getId().toString().equals(command.getCampaignId())) {
            sendSnapshot(
                server,
                connectionId,
                sender,
                "The campaign changed; reopen Campaign content.",
                command.getRequestId());
            return;
          }
          if (!command.getUpdate()) {
            sendSnapshot(server, connectionId, sender, "", command.getRequestId());
            return;
          }
          if (!sender.isGM()) {
            sendSnapshot(
                server,
                connectionId,
                sender,
                "Only the GM may edit campaign content.",
                command.getRequestId());
            return;
          }
          try {
            if (!campaign.updateCampaignContent(command.getExpectedRevision(), command.getJson())) {
              sendSnapshot(
                  server,
                  connectionId,
                  sender,
                  "Content changed on another client. The latest version has been loaded; retry"
                      + " your edit.",
                  command.getRequestId());
              return;
            }
            sendSnapshot(server, connectionId, sender, "", command.getRequestId());
            server.sendCampaignContentSnapshots();
          } catch (IllegalArgumentException failure) {
            sendSnapshot(
                server, connectionId, sender, failure.getMessage(), command.getRequestId());
          }
        });
  }

  public static void sendSnapshot(
      MapToolServer server, String connectionId, Player recipient, String error) {
    sendSnapshot(server, connectionId, recipient, error, "");
  }

  private static void sendSnapshot(
      MapToolServer server, String connectionId, Player recipient, String error, String requestId) {
    var campaign = server.getCampaign();
    var state = campaign.getCampaignContentState();
    var store = ContentStore.fromJson(state.json());
    var viewer = new Viewer(recipient.getName().toLowerCase(Locale.ROOT), recipient.isGM());
    // Fail closed until map visibility can be evaluated for the particular recipient. A SHARED
    // pin is an explicit handout independent of fog; VISIBLE_AREA pins are withheld here.
    String json = recipient.isGM() ? store.toJson() : store.snapshot(viewer, pin -> false).toJson();
    var snapshot =
        CampaignContentSnapshotMsg.newBuilder()
            .setCampaignId(campaign.getId().toString())
            .setRevision(state.revision())
            .setJson(json)
            .setError(error)
            .setRequestId(requestId);
    server.sendMessage(
        connectionId, Message.newBuilder().setCampaignContentSnapshotMsg(snapshot).build());
  }
}
