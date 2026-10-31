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
package net.rptools.maptool.model.library.proto;

import static org.junit.jupiter.api.Assertions.*;

import com.google.protobuf.util.JsonFormat;
import org.junit.jupiter.api.Test;

class AddOnLibraryCompatibilityDtoTest {
  @Test
  void legacyMetadataStillRoundTripsWithoutRequirements() throws Exception {
    var builder = AddOnLibraryDto.newBuilder();
    JsonFormat.parser()
        .merge("{\"namespace\":\"example.legacy\",\"version\":\"old-beta\"}", builder);
    var legacy = builder.build();
    assertEquals("", legacy.getSupportedMapToolVersions());
    assertTrue(legacy.getDependenciesList().isEmpty());
    assertEquals(legacy, AddOnLibraryDto.parseFrom(legacy.toByteArray()));
    var reloaded = AddOnLibraryDto.newBuilder();
    JsonFormat.parser().merge(JsonFormat.printer().print(legacy), reloaded);
    assertEquals(legacy, reloaded.build());
  }

  @Test
  void newRequirementsSurviveCampaignListAndJsonWireRoundTrips() throws Exception {
    var builder = AddOnLibraryDto.newBuilder();
    JsonFormat.parser()
        .merge(
            """
{"namespace":"example.app","version":"1.2.0","supportedMapToolVersions":">=1.18.0 <2.0.0",
 "dependencies":[{"namespace":"example.core","versionRange":"^1.0.0"},
                 {"namespace":"example.extra","versionRange":"~2.0.0","optional":true}]}
""",
            builder);
    var details = builder.build();
    var campaign =
        AddOnLibraryListDto.newBuilder()
            .addLibraries(
                AddOnLibraryListDto.AddOnLibraryEntryDto.newBuilder()
                    .setDetails(details)
                    .setMd5Hash("example"))
            .build();
    var reloaded = AddOnLibraryListDto.newBuilder();
    JsonFormat.parser().merge(JsonFormat.printer().print(campaign), reloaded);
    var restored = reloaded.build().getLibraries(0).getDetails();
    assertEquals(details, restored);
    assertEquals(2, restored.getDependenciesCount());
    assertFalse(restored.getDependencies(0).getOptional());
    assertTrue(restored.getDependencies(1).getOptional());
    assertEquals("^1.0.0", restored.getDependencies(0).getVersionRange());
    assertEquals(campaign, AddOnLibraryListDto.parseFrom(campaign.toByteArray()));
  }
}
