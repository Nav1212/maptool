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

import net.rptools.maptool.server.proto.CampaignDto;
import net.rptools.maptool.server.proto.Message;
import net.rptools.maptool.server.proto.SetCampaignMsg;

/** Constructs the public half of an authenticated private campaign replacement. */
public final class CampaignContentWire {
  private CampaignContentWire() {}

  public static Message publicReplacement(SetCampaignMsg request, CampaignDto canonicalCampaign) {
    return Message.newBuilder()
        .setSetCampaignMsg(
            request.toBuilder().setCampaign(canonicalCampaign).clearPrivateContentJson())
        .build();
  }
}
