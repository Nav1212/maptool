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

import static org.junit.jupiter.api.Assertions.*;

import net.rptools.maptool.server.proto.CampaignDto;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.SetCampaignMsg;
import org.junit.jupiter.api.Test;

class CampaignContentWireTest {
  @Test
  void replacementBroadcastStripsPrivateJsonAndUsesCanonicalEpoch() throws Exception {
    var request =
        SetCampaignMsg.newBuilder()
            .setCampaign(CampaignDto.newBuilder().setId("campaign").setCampaignContentRevision(4))
            .setPrivateContentJson("secret-journal-body")
            .build();
    var canonical = request.getCampaign().toBuilder().setCampaignContentRevision(12).build();
    var wire = CampaignContentWire.publicReplacement(request, canonical);
    var received = Message.parseFrom(wire.toByteArray()).getSetCampaignMsg();
    assertFalse(received.hasPrivateContentJson());
    assertFalse(wire.toString().contains("secret-journal-body"));
    assertEquals(12, received.getCampaign().getCampaignContentRevision());
    assertEquals("secret-journal-body", request.getPrivateContentJson());
  }

  @Test
  void legacyReplacementWithoutPrivateFieldStaysCompatible() {
    var request =
        SetCampaignMsg.newBuilder().setCampaign(CampaignDto.newBuilder().setId("old")).build();
    assertFalse(request.hasPrivateContentJson());
    assertEquals(0, request.getCampaign().getCampaignContentRevision());
    assertFalse(
        CampaignContentWire.publicReplacement(request, request.getCampaign())
            .getSetCampaignMsg()
            .hasPrivateContentJson());
    assertEquals(1, SetCampaignMsg.getDescriptor().findFieldByName("campaign").getNumber());
    assertEquals(
        2, SetCampaignMsg.getDescriptor().findFieldByName("private_content_json").getNumber());
  }
}
